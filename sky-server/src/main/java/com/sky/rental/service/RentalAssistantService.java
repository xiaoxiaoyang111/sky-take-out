package com.sky.rental.service;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.model.*;
import com.sky.rental.model.RentalAssistantModels.*;
import com.sky.result.PageResult;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class RentalAssistantService {
    static final String RULES="按自然日计费，领取日计费、归还日不计费；1日至3日为2天。租期1至30天，开始日在今天至未来180天内。预约后等待门店确认，到店领取及归还。租金和押金在线下办理，无线上付款、自动退款或自动赔偿。待确认和待领取订单可取消。领取和归还需核对实物编号、外观、功能及配件。卡口兼容性、证件要求、门店地址和营业时间尚无完整资料，请向门店确认。报价不锁定库存，提交预约时再次校验。";
    private final RentalAssistantStore store;
    private final RentalModelClient model;
    private final RentalBookingService booking;
    private final RentalCatalogService catalog;
    public RentalAssistantService(RentalAssistantStore store,RentalModelClient model,RentalBookingService booking,RentalCatalogService catalog) { this.store=store;this.model=model;this.booking=booking;this.catalog=catalog; }
    public Map<String,Object> status() { Map<String,Object> s=new LinkedHashMap<>();s.put("modelConfigured",model.configured());s.put("historyMinutes",30);s.put("minuteLimit",6);return s; }
    public List<Turn> history(String session) { return store.history(user(),session(session)); }
    public void clear(String session) {
        Long uid=user();String valid=session(session);String lease=store.acquire(uid);
        try { store.clear(uid,valid); } finally { store.release(uid,lease); }
    }
    public Turn ask(Request request) {
        Long uid=user();
        if(request==null) throw new BaseException("咨询参数不能为空");
        String sid=session(request.getSessionId()), question=request.getMessage()==null?"":request.getMessage().trim();
        if(question.isEmpty()||question.length()>600) throw new BaseException("咨询内容须为1至600字符");
        String action=request.getAction();
        if(!Arrays.asList("ASK","RULES","QUOTE","ORDERS").contains(action)) throw new BaseException("不支持的咨询操作");
        String lease=store.acquire(uid);
        try {
            Turn turn=new Turn();turn.setQuestion(question);turn.setCreatedAt(Instant.now().toString());
            if("ORDERS".equals(action)||"ASK".equals(action)&&question.contains("订单")) {
                PageResult page=booking.list(false,1,5,null,null);
                StringBuilder text=new StringBuilder("本人最近订单（最多5笔）：\n");
                for(Object row:page.getRecords()) { RentalOrder o=(RentalOrder)row;text.append(o.getEquipmentName()).append("｜").append(state(o.getStatus())).append("｜").append(o.getStartDate()).append(" 至 ").append(o.getEndDate()).append("\n"); }
                if(page.getRecords().isEmpty()) text.append("暂无订单。\n");
                turn.setMode("LIVE");turn.setAnswer(text+"详情及取消操作请前往“我的租赁”。");turn.setSources(Collections.singletonList("/orders"));
            } else if("QUOTE".equals(action)) {
                RentalQuote q=booking.quote(request.getEquipmentId(),request.getStartDate(),request.getEndDate());
                turn.setMode("LIVE");turn.setAnswer("所选租期 "+q.getStartDate()+" 至 "+q.getEndDate()+"，共"+q.getRentalDays()+"天。当前可租 "+q.getAvailableCount()+" 件。租金 ¥"+q.getRentalAmount()+"，押金 ¥"+q.getDepositAmount()+"，合计 ¥"+q.getTotalAmount()+"，到店办理。查询不锁定库存。");turn.setSources(Collections.singletonList("/equipment/"+q.getEquipmentId()));
            } else if("RULES".equals(action)||question.matches(".*(押金|计费|归还|取消|领取|租期|规则|须知|退款).*")) {
                turn.setMode("RULES");turn.setAnswer(RULES);turn.setSources(Collections.singletonList("/guide"));
            } else {
                if(model.configured()&&!Boolean.TRUE.equals(request.getAllowModel())) throw new BaseException("请先确认将咨询内容和器材资料发送给模型服务");
                String context=RULES;
                if(request.getEquipmentId()!=null) { RentalEquipment e=catalog.equipment(request.getEquipmentId(),false);context+="\n当前器材："+e.getName()+"；品牌："+e.getBrand()+"；说明："+e.getDescription()+"；日租金："+e.getDailyRate()+"；押金："+e.getDeposit(); }
                List<Map<String,String>> history=new ArrayList<>();
                List<Turn> previous=store.history(uid,sid);
                for(Turn t:previous.subList(Math.max(0,previous.size()-3),previous.size())) if("MODEL".equals(t.getMode())) { history.add(RentalModelClient.message("user",t.getQuestion()));history.add(RentalModelClient.message("assistant",t.getAnswer())); }
                turn.setAnswer(model.answer(context,history,question));turn.setMode("MODEL");turn.setSources(Collections.singletonList("/guide"));
            }
            store.append(uid,sid,turn);return turn;
        } finally { store.release(uid,lease); }
    }
    private static Long user() { if(RentalContext.getUserId()==null||!"USER".equals(RentalContext.getRole())) throw new BaseException("请使用租赁用户账号登录");return RentalContext.getUserId(); }
    private static String session(String id) { if(id==null||!id.matches("[a-zA-Z0-9-]{16,64}")) throw new BaseException("会话编号无效");return id; }
    private static String state(String s) { switch(s) {case "PENDING":return "待确认";case "CONFIRMED":return "待领取";case "RENTED":return "租用中";case "COMPLETED":return "已归还";case "CANCELLED":return "已取消";default:return "已拒绝";} }
}
