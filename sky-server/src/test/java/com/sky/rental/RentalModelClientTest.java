package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.service.RentalModelClient;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RentalModelClientTest {
    HttpServer server;
    @AfterEach void close(){if(server!=null)server.stop(0);}
    RentalModelClient client(String path){return new RentalModelClient(true,"http://127.0.0.1:"+server.getAddress().getPort()+path,"synthetic-test-key","test-model","max_completion_tokens");}
    @Test void compatibleProviderReceivesBoundedMessagesAndTokenBudget() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicReference<String> body=new AtomicReference<>(),auth=new AtomicReference<>();
        server.createContext("/chat",e->{body.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));auth.set(e.getRequestHeaders().getFirst("Authorization"));byte[] response="{\"choices\":[{\"message\":{\"content\":\"请确认卡口兼容性\"}}]}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,response.length);e.getResponseBody().write(response);e.close();});server.start();
        assertEquals("请确认卡口兼容性",client("/chat").answer("门店规则",Collections.emptyList(),"帮我选镜头"));assertEquals("Bearer synthetic-test-key",auth.get());assertEquals(600,new ObjectMapper().readTree(body.get()).path("max_completion_tokens").asInt());assertFalse(body.get().contains("PRIVATE_ORDER"));
    }
    @Test void providerFailureDoesNotLeakVendorBodyOrCredential() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat",e->{byte[] data="synthetic-test-key vendor diagnostic".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(503,data.length);e.getResponseBody().write(data);e.close();});server.start();BaseException ex=assertThrows(BaseException.class,()->client("/chat").answer("rules",Collections.emptyList(),"question"));assertFalse(ex.getMessage().contains("synthetic-test-key"));
    }
    @Test void disabledModelDoesNotAttemptNetwork() { RentalModelClient c=new RentalModelClient(false,"","","","max_completion_tokens");assertFalse(c.configured());assertTrue(assertThrows(BaseException.class,()->c.answer("",Collections.emptyList(),"hi")).getMessage().contains("尚未配置")); }
    @Test void nonTlsRemoteEndpointRejected() { RentalModelClient c=new RentalModelClient(true,"http://example.com/chat","test","test","max_completion_tokens");assertThrows(BaseException.class,()->c.answer("",Collections.emptyList(),"hi")); }
}
