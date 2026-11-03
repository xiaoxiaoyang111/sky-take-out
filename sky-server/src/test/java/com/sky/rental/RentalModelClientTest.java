package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.service.RentalModelClient;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RentalModelClientTest {
    @Test void globalConcurrencyIsBoundedAndCapacityReturnsAfterCompletion() throws Exception {
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.net.HttpURLConnection connection=org.mockito.Mockito.mock(java.net.HttpURLConnection.class);
        org.mockito.Mockito.when(connection.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
        org.mockito.Mockito.when(connection.getResponseCode()).thenAnswer(call->{entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException();return 200;});
        org.mockito.Mockito.when(connection.getInputStream()).thenAnswer(call->new java.io.ByteArrayInputStream("{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}".getBytes(StandardCharsets.UTF_8)));
        RentalModelClient c=new RentalModelClient(true,"http://127.0.0.1/chat","synthetic","test","max_tokens",false,3000,45000,"",1){@Override protected java.net.HttpURLConnection openConnection(java.net.URI uri){return connection;}};
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{java.util.concurrent.Future<String> first=worker.submit(()->c.answer("",Collections.emptyList(),"hello"));assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(assertThrows(BaseException.class,()->c.answer("",Collections.emptyList(),"second")).getMessage().contains("咨询较多"));release.countDown();assertEquals("你好",first.get(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals("你好",c.answer("",Collections.emptyList(),"third"));org.mockito.Mockito.verify(connection,org.mockito.Mockito.times(2)).getResponseCode();}
        finally{release.countDown();worker.shutdownNow();}
    }
    HttpServer server;
    @AfterEach void close(){if(server!=null)server.stop(0);}
    RentalModelClient client(String path){return new RentalModelClient(true,"http://127.0.0.1:"+server.getAddress().getPort()+path,"synthetic-test-key","test-model","max_completion_tokens");}
    @Test void compatibleProviderReceivesBoundedMessagesAndTokenBudget() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicReference<String> body=new AtomicReference<>(),auth=new AtomicReference<>();
        server.createContext("/chat",e->{body.set(org.springframework.util.StreamUtils.copyToString(e.getRequestBody(),StandardCharsets.UTF_8));auth.set(e.getRequestHeaders().getFirst("Authorization"));byte[] response="{\"choices\":[{\"message\":{\"content\":\"请确认卡口兼容性\"}}]}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,response.length);e.getResponseBody().write(response);e.close();});server.start();
        assertEquals("请确认卡口兼容性",client("/chat").answer("门店规则",Collections.emptyList(),"帮我选镜头"));assertEquals("Bearer synthetic-test-key",auth.get());assertEquals(600,new ObjectMapper().readTree(body.get()).path("max_completion_tokens").asInt());assertFalse(body.get().contains("PRIVATE_ORDER"));
    }
    @Test void providerFailureDoesNotLeakVendorBodyOrCredential() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat",e->{byte[] data="synthetic-test-key vendor diagnostic".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(503,data.length);e.getResponseBody().write(data);e.close();});server.start();BaseException ex=assertThrows(BaseException.class,()->client("/chat").answer("rules",Collections.emptyList(),"question"));assertFalse(ex.getMessage().contains("synthetic-test-key"));
    }
    @Test void disabledModelDoesNotAttemptNetwork() { RentalModelClient c=new RentalModelClient(false,"","","","max_completion_tokens");assertFalse(c.configured());assertTrue(assertThrows(BaseException.class,()->c.answer("",Collections.emptyList(),"hi")).getMessage().contains("尚未配置")); }
    @Test void deepSeekUsesMaxTokensAndDisablesThinking() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicReference<String> body=new AtomicReference<>();
        server.createContext("/chat",e->{body.set(org.springframework.util.StreamUtils.copyToString(e.getRequestBody(),StandardCharsets.UTF_8));byte[] data="{\"choices\":[{\"message\":{\"content\":\"先确认拍摄需求\"}}]}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();});server.start();
        RentalModelClient client=new RentalModelClient(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/chat","synthetic-test-key","deepseek-flash","max_tokens",true);
        assertEquals("先确认拍摄需求",client.answer("rules",Collections.singletonList(RentalModelClient.message("user","之前的问题")),"这次的问题"));
        com.fasterxml.jackson.databind.JsonNode sent=new ObjectMapper().readTree(body.get());assertEquals(600,sent.path("max_tokens").asInt());assertFalse(sent.has("max_completion_tokens"));assertEquals("disabled",sent.path("thinking").path("type").asText());assertEquals("deepseek-flash",sent.path("model").asText());assertEquals(3,sent.path("messages").size());
    }
    @ParameterizedTest @CsvSource({"401,密钥无效","402,余额不足","429,请求频繁"})
    void providerErrorsAreActionableAndRedacted(int code,String expected) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat",e->{byte[] data="sensitive vendor diagnostic".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(code,data.length);e.getResponseBody().write(data);e.close();});server.start();
        String message=assertThrows(BaseException.class,()->client("/chat").answer("rules",Collections.emptyList(),"hi")).getMessage();assertTrue(message.contains(expected));assertFalse(message.contains("sensitive"));
    }
    @Test void nonTlsRemoteEndpointRejected() { RentalModelClient c=new RentalModelClient(true,"http://example.com/chat","test","test","max_completion_tokens");assertThrows(BaseException.class,()->c.answer("",Collections.emptyList(),"hi")); }
    @ParameterizedTest @CsvSource({"400,参数","404,参数","422,参数","500,服务商暂时不可用","503,服务商暂时不可用"})
    void upstreamFailuresAreDistinguished(int status,String text) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat",e->{e.sendResponseHeaders(status,-1);e.close();});server.start();
        assertTrue(assertThrows(BaseException.class,()->client("/chat").answer("rules",Collections.emptyList(),"question")).getMessage().contains(text));
    }
    @Test void emptySuccessfulResponseIsNotAcceptedAsAnAnswer() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat",e->{byte[] data="{\"choices\":[]}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();});server.start();
        assertTrue(assertThrows(BaseException.class,()->client("/chat").answer("rules",Collections.emptyList(),"question")).getMessage().contains("有效文本"));
    }
    @Test void timeoutIsDistinctAndNeverRetried() throws Exception {
        java.net.HttpURLConnection connection=org.mockito.Mockito.mock(java.net.HttpURLConnection.class);
        org.mockito.Mockito.when(connection.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
        org.mockito.Mockito.when(connection.getResponseCode()).thenThrow(new java.net.SocketTimeoutException("sensitive diagnostic"));
        RentalModelClient c=new RentalModelClient(true,"http://127.0.0.1/chat","synthetic-test-key","test","max_tokens") {
            @Override protected java.net.HttpURLConnection openConnection(java.net.URI uri) { return connection; }
        };
        String error=assertThrows(BaseException.class,()->c.answer("rules",Collections.emptyList(),"question")).getMessage();assertTrue(error.contains("超时"));assertFalse(error.contains("sensitive"));org.mockito.Mockito.verify(connection,org.mockito.Mockito.times(1)).getResponseCode();org.mockito.Mockito.verify(connection).disconnect();
    }
    @Test void redirectDoesNotForwardCredentialsToAnotherEndpoint() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);java.util.concurrent.atomic.AtomicInteger forwarded=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/redirect",e->{e.getResponseHeaders().set("Location","http://127.0.0.1:"+server.getAddress().getPort()+"/target");e.sendResponseHeaders(302,-1);e.close();});
        server.createContext("/target",e->{forwarded.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});server.start();
        assertThrows(BaseException.class,()->client("/redirect").answer("rules",Collections.emptyList(),"hi"));assertEquals(0,forwarded.get());
    }
    @Test void defaultTimeoutAllowsSlowerModelResponsesWithoutUnlimitedWaiting() throws Exception {
        class InspectableClient extends RentalModelClient {
            InspectableClient(){super(true,"http://127.0.0.1/chat","test","test","max_tokens");}
            java.net.HttpURLConnection connection() throws java.io.IOException {return openConnection(java.net.URI.create("http://127.0.0.1/chat"));}
        }
        java.net.HttpURLConnection connection=new InspectableClient().connection();
        try{assertEquals(3000,connection.getConnectTimeout());assertEquals(45000,connection.getReadTimeout());assertFalse(connection.getInstanceFollowRedirects());}
        finally{connection.disconnect();}
    }
    @Test void invalidTimeoutCannotDisableTimeoutOrExceedAllowedWindow() {
        for(int[] values:new int[][]{{0,45000},{5001,45000},{3000,0},{3000,45001}})
            assertThrows(IllegalArgumentException.class,()->new RentalModelClient(true,"http://127.0.0.1/chat","test","test","max_tokens",false,values[0],values[1]));
    }
    @Test void delayedProviderWithinConfiguredReadTimeoutSucceeds() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",e->{
            try{Thread.sleep(200);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            byte[] data="{\"choices\":[{\"message\":{\"content\":\"你好，我是小影\"}}]}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();
        });server.start();
        RentalModelClient c=new RentalModelClient(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/chat","test","test","max_tokens",false,1000,2000);
        assertEquals("你好，我是小影",c.answer("rules",Collections.emptyList(),"你是谁"));
    }
    @Test void explicitlyConfiguredProxyRoutesOnlyThisClientWithoutGlobalProperties() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> target=new AtomicReference<>();
        server.createContext("/",e->{target.set(e.getRequestURI().toString());e.sendResponseHeaders(401,-1);e.close();});server.start();
        String before=System.getProperty("http.proxyHost");
        class ProxiedClient extends RentalModelClient {
            ProxiedClient(){super(true,"https://example.invalid/chat","synthetic","test","max_tokens",false,1000,2000,"http://127.0.0.1:"+server.getAddress().getPort());}
            java.net.HttpURLConnection connection() throws Exception {return openConnection(java.net.URI.create("http://upstream.invalid/models"));}
        }
        java.net.HttpURLConnection connection=new ProxiedClient().connection();
        try{connection.setRequestMethod("GET");assertTrue(connection.usingProxy());assertEquals(401,connection.getResponseCode());assertEquals("http://upstream.invalid/models",target.get());assertEquals(before,System.getProperty("http.proxyHost"));}
        finally{connection.disconnect();}
    }
    @Test void invalidProxyIsRejectedWithoutLeakingCredentials() {
        for(String value:Arrays.asList("socks://localhost:7890","http://user:secret@localhost:7890","http://localhost","http://localhost:7890/path","http://localhost:7890?token=secret","http://localhost:7890/#secret")) {
            String error=assertThrows(IllegalArgumentException.class,()->new RentalModelClient(true,"https://example.invalid/chat","test","test","max_tokens",false,3000,45000,value)).getMessage();
            assertFalse(error.contains("secret"));assertFalse(error.contains(value));
        }
    }
}
