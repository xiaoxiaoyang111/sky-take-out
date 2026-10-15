package com.sky.service;

public interface ShopService {
    /**
     * 查询营业状态:1:营业中,0: 打烊
     * @return
     */
    Integer getStatus();

    /** 设置营业状态：1 营业中，0 打烊。 */
    void setStatus(Integer status);

}
