package com.jbetfairng;

import com.google.common.base.Optional;
import com.jbetfairng.entities.*;
import com.google.gson.Gson;
import com.jbetfairng.enums.*;
import com.jbetfairng.exceptions.LoginException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.util.HashMap;
import java.util.List;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;

import com.google.common.reflect.TypeToken;
import com.google.common.reflect.TypeParameter;

public class BetfairClient {

    private Exchange exchange;
    private Network networkClient;
    private String appKey;
    private String sessionToken;
    private Logger tracer;

    private static String LIST_COMPETITIONS_METHOD = "SportsAPING/v1.0/listCompetitions";
    private static String LIST_COUNTRIES_METHOD = "SportsAPING/v1.0/listCountries";
    private static String LIST_CURRENT_ORDERS_METHOD = "SportsAPING/v1.0/listCurrentOrders";
    private static String LIST_CLEARED_ORDERS_METHOD = "SportsAPING/v1.0/listClearedOrders";
    private static String LIST_EVENT_TYPES_METHOD = "SportsAPING/v1.0/listEventTypes";
    private static String LIST_MARKET_CATALOGUE_METHOD = "SportsAPING/v1.0/listMarketCatalogue";
    private static String LIST_MARKET_BOOK_METHOD = "SportsAPING/v1.0/listMarketBook";
    private static String LIST_MARKET_PROFIT_AND_LOSS = "SportsAPING/v1.0/listMarketProfitAndLoss";
    private static String LIST_MARKET_TYPES = "SportsAPING/v1.0/listMarketTypes";
    private static String LIST_TIME_RANGES = "SportsAPING/v1.0/listTimeRanges";
    private static String LIST_VENUES = "SportsAPING/v1.0/listVenues";
    private static String PLACE_ORDERS_METHOD = "SportsAPING/v1.0/placeOrders";
    private static String CANCEL_ORDERS_METHOD = "SportsAPING/v1.0/cancelOrders";
    private static String REPLACE_ORDERS_METHOD = "SportsAPING/v1.0/replaceOrders";
    private static String UPDATE_ORDERS_METHOD = "SportsAPING/v1.0/updateOrders";

    private static String FILTER = "filter";
    private static String BET_IDS = "betIds";
    private static String RUNNER_IDS = "runnerIds";
    private static String SIDE = "side";
    private static String SETTLED_DATE_RANGE = "settledDateRange";
    private static String EVENT_TYPE_IDS = "eventTypeIds";
    private static String EVENT_IDS = "eventIds";
    private static String BET_STATUS = "betStatus";
    private static String PLACED_DATE_RANGE = "placedDateRange";
    private static String DATE_RANGE = "dateRange";
    private static String ORDER_BY = "orderBy";
    private static String GROUP_BY = "groupBy";
    private static String SORT_DIR = "sortDir";
    private static String FROM_RECORD = "fromRecord";
    private static String RECORD_COUNT = "recordCount";
    private static String GRANULARITY = "granularity";
    private static String MARKET_PROJECTION = "marketProjection";
    private static String MATCH_PROJECTION = "matchProjection";
    private static String ORDER_PROJECTION = "orderProjection";
    private static String PRICE_PROJECTION = "priceProjection";
    private static String SORT = "sort";
    private static String MAX_RESULTS = "maxResults";
    private static String MARKET_IDS = "marketIds";
    private static String MARKET_ID = "marketId";
    private static String INSTRUCTIONS = "instructions";
    private static String CUSTOMER_REFERENCE = "customerRef";
    private static String INCLUDE_SETTLED_BETS = "includeSettledBets";
    private static String INCLUDE_BSP_BETS = "includeBspBets";
    private static String INCLUDE_ITEM_DESCRIPTION = "includeItemDescription";
    private static String NET_OF_COMMISSION = "netOfCommission";
    private static String FROM_CURRENCY = "fromCurrency";
    private static String FROM = "from";
    private static String TO = "to";
    private static String AMOUNT = "amount";
    private static String WALLET = "wallet";


    public BetfairClient(Exchange exchange, String appKey) {
        this.tracer = LogManager.getFormatterLogger("BetfairClient");
        this.exchange = exchange;
        this.appKey = appKey;
    }

    public Boolean login(
            String p12CertificateLocation,
            String p12CertificatePassword,
            String username,
            String password) throws LoginException {

        if (Helpers.isNullOrWhitespace(p12CertificateLocation))
            throw new IllegalArgumentException(p12CertificateLocation);
        if (Helpers.isNullOrWhitespace(p12CertificatePassword))
            throw new IllegalArgumentException(username);
        if (Helpers.isNullOrWhitespace(username))
            throw new IllegalArgumentException(username);
        if (Helpers.isNullOrWhitespace(password))
            throw new IllegalArgumentException(password);

        FileInputStream keyStream = null;
        try {
            KeyStore clientStore = KeyStore.getInstance("PKCS12");
            keyStream = new FileInputStream(p12CertificateLocation);
            clientStore.load(keyStream, p12CertificatePassword.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(clientStore, p12CertificatePassword.toCharArray());
            KeyManager[] kms = kmf.getKeyManagers();

            SSLContext sslContext = null;
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kms, null, new SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            URL url = new URL("https://identitysso-api.betfair.com/api/certlogin");

            String postData = String.format("username=%s&password=%s", username, password);

            HttpsURLConnection request = (HttpsURLConnection) url.openConnection();
            request.setRequestMethod("POST");
            request.setRequestProperty("X-Application", this.appKey);
            request.setDoOutput(true);
            DataOutputStream writer = new DataOutputStream(request.getOutputStream());
            writer.writeBytes(postData);
            writer.flush();
            writer.close();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(request.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            System.out.println(response.toString());
            Gson gson = new Gson();
            LoginResponse loginResult = gson.fromJson(response.toString(), LoginResponse.class);
            if (loginResult.loginStatus.equals("SUCCESS")) {
                this.sessionToken = loginResult.sessionToken;
                this.networkClient = new Network(this.appKey, this.sessionToken, false);
                return true;
            }
            else {
                return false;
            }
        } catch (Exception ex) {
            throw new LoginException(ex);
        } finally {
            try {
                keyStream.close();
            } catch (Exception ignore) {}
        }
    }

    public BetfairServerResponse<KeepAliveResponse> keepAlive() {
        return networkClient.keepAliveSynchronous();
    }

    public BetfairServerResponse<List<CompetitionResult>> listCompetitions(MarketFilter marketFilter) {
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put(FILTER, marketFilter);
        return networkClient.Invoke(
                new TypeToken<List<CompetitionResult>>() { },
                this.exchange,
                Endpoint.Betting,
                LIST_COMPETITIONS_METHOD,
                args);
    }

    public BetfairServerResponse<List<CountryCodeResult>> listCountries(MarketFilter marketFilter) {
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put(FILTER, marketFilter);
        return networkClient.Invoke(
                new TypeToken<List<CountryCodeResult>>() { },
                this.exchange,
                Endpoint.Betting,
                LIST_COUNTRIES_METHOD,
                args);
    }

    public BetfairServerResponse<List<EventResult>> listEvents(MarketFilter marketFilter) {
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put(FILTER, marketFilter);
        return networkClient.Invoke(
                new TypeToken<List<EventResult>>() { },
                this.exchange,
                Endpoint.Betting,
                LIST_EVENT_TYPES_METHOD,
                args);
    }

    public BetfairServerResponse<CurrentOrderSummaryReport> listCurrentOrders(
            Set<String> betIds,
            Set<String> marketIds,
            OrderProjection orderProjection,
            TimeRange placedDateRange,
            TimeRange dateRange,
            OrderBy orderBy,
            SortDir sortDir,
            Optional<Integer> fromRecord,
            Optional<Integer> recordCount)
    {
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put(BET_IDS, betIds);
        args.put(MARKET_IDS, marketIds);
        args.put(ORDER_PROJECTION, orderProjection);
        args.put(PLACED_DATE_RANGE, placedDateRange);
        args.put(DATE_RANGE, dateRange);
        args.put(ORDER_BY, orderBy);
        args.put(SORT_DIR, sortDir);
        args.put(FROM_RECORD, fromRecord);
        args.put(RECORD_COUNT, recordCount);
        return networkClient.Invoke(
                new TypeToken<CurrentOrderSummaryReport>() { },
                this.exchange,
                Endpoint.Betting,
                LIST_CURRENT_ORDERS_METHOD,
                args);
    }
}