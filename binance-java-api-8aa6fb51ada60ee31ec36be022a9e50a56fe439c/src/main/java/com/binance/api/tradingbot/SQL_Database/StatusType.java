package com.binance.api.tradingbot.SQL_Database;

public enum StatusType {
  
    ROI("ROI"),    
   
    buyStatus("buystatus"),    
    
    sellStatus("sellstatus");

    private final String columnName;
  
    StatusType(String columnName) {
        this.columnName = columnName;
    }
   
    public String getColumnName() {
        return columnName;
    }

    public static StatusType fromColumnName(String columnName) {
        for (StatusType type : values()) {
            if (type.columnName.equals(columnName)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
            "Ungültiger Spaltenname: " + columnName + 
            ". Erlaubt sind: ROI_status, BUYING, SELLING"
        );
    }
}
