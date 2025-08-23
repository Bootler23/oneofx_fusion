package com.binance.api.tradingbot.Settings;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;

public class BinanceConfig {    
   
    private static final String API_KEY = "3p2AemKbKyaKUocMgQuMY442UaAgScPriHcSsS57fvt5y1iP5LLCV2jqQVovQBNv";
    private static final String API_SECRET = "HZXnE4kFzRHPHO8Dr7B9v4HkLocCCG3UmuRIhxZaHlm3i24mA3Fei9kCv3Kq1zGI";    

    public static BinanceApiRestClient createRestClient() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(API_KEY, API_SECRET);
        return factory.newRestClient();
    }   
}
