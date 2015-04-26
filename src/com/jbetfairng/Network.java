package com.jbetfairng;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jbetfairng.enums.Endpoint;
import com.jbetfairng.enums.Exchange;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import rx.Observable;
import rx.apache.http.ObservableHttp;
import rx.apache.http.ObservableHttpResponse;
import rx.functions.Func1;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class Network {

    private String appKey;
    private String sessionToken;
    private Boolean gzipCompress;
    private Logger tracer;

    public Network(
            String appKey,
            String sessionToken,
            Boolean gzipCompress)
    {
        this.appKey = appKey;
        this.sessionToken = sessionToken;
        this.gzipCompress = gzipCompress;
        this.tracer = LogManager.getFormatterLogger("Network");
    }

    public <T> BetfairServerResponse<T> Invoke(
            Class<T> entityClass,
            Exchange exchange,
            Endpoint endpoint,
            String method,
            Map<String, Object> args)
    {
        if (Helpers.isNullOrWhitespace(method)) throw new IllegalArgumentException(method);
        tracer.debug("%s, %s", formatEndpoint(endpoint), method);

        DateTime requestStart = DateTime.now();
        long requestStartMillis = System.currentTimeMillis();

        String url = "";
        if (exchange == Exchange.AUS)
            url = "https://api-au.betfair.com/exchange";
        else
            url = "https://api.betfair.com/exchange";

        if (endpoint == Endpoint.Betting)
            url += "/betting/json-rpc/v1";
        else
            url += "/account/json-rpc/v1";

        JsonRequest call = new JsonRequest();
        call.method = method;
        call.params = args;
        call.id = 1;

        String requestData = new Gson().toJson(call);
        String result = RequestSync(url, requestData, ContentType.APPLICATION_JSON, appKey, sessionToken);

        Gson gson = new Gson();
        Type typeToken = new TypeToken<JsonResponse<T>>() { }.getType();
        JsonResponse<T> entity = gson.fromJson(result, typeToken);
        // should be returning Observable<Betfair...> here.
        if (entity != null) {
            BetfairServerResponse<T> response = new BetfairServerResponse<T>(
                    entity.result,
                    DateTime.now(),
                    requestStart,
                    (requestStartMillis - System.currentTimeMillis()) / 1000,
                    entity.hasError);
            return response;
        }
        else
            return new BetfairServerResponse<T>(
                    null,
                    DateTime.now(),
                    requestStart,
                    (requestStartMillis - System.currentTimeMillis()) / 1000,
                    true);
    }

    private String RequestSync(
            String url,
            String requestPostData,
            ContentType contentType,
            String appKey,
            String sessionToken)
    {
        Header[] headers = {
                new BasicHeader("X-Application", appKey),
                new BasicHeader("X-Authentication", sessionToken),
                new BasicHeader("Cache-Control", "no-cache"),
                new BasicHeader("Pragma", "no-cache")
        };

        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultHeaders(new ArrayList<Header>(Arrays.asList(headers)))
                .build();
        try {
            StringEntity entity = new StringEntity(requestPostData);
            HttpPost post = new HttpPost(url);
            post.setEntity(entity);
            HttpResponse response = client.execute(post);
            String json = EntityUtils.toString(response.getEntity(), "UTF-8");
            return json;
        }
        catch (IOException exception)
        {
            return null;
        }
    }

    private Observable<String> Request(
            String url,
            String requestPostData,
            ContentType contentType,
            String appKey,
            String sessionToken) throws UnsupportedEncodingException {

        Header[] headers = {
                new BasicHeader("X-Application", appKey),
                new BasicHeader("X-Authentication", sessionToken),
                new BasicHeader("Cache-Control", "no-cache"),
                new BasicHeader("Pragma", "no-cache")
        };

        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(500).build();
        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnPerRoute(20)
                .setMaxConnTotal(50)
                .setDefaultHeaders(new ArrayList<Header>(Arrays.asList(headers)))
                .build();

        return ObservableHttp.createRequest(HttpAsyncMethods.createPost(url, requestPostData, contentType), httpclient)
            .toObservable()
            .flatMap(new Func1<ObservableHttpResponse, Observable<String>>() {
                public Observable<String> call(ObservableHttpResponse response) {
                    return response.getContent().map(new Func1<byte[], String>() {
                        public String call(byte[] bb) {
                            return new String(bb);
                        }
                    });
                }
            });
    }


    private String formatEndpoint(Endpoint endpoint) {
        return endpoint == Endpoint.Betting ? "betting" : "account";
    }
}
