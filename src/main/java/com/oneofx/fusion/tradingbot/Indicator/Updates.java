package com.oneofx.fusion.tradingbot.Indicator;

import com.oneofx.fusion.tradingbot.SQL_Database.HistDAO;
import com.oneofx.fusion.tradingbot.SQL_Database.SETSQL;

public class Updates {

    private static final HistDAO histDAO = new HistDAO();

    public static void NewCounterPosition() {
        SETSQL.updateCount(histDAO.getCountHist());
    }
}
