package com.binance.api.client.domain.general;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Filters define trading rules on a symbol or an exchange. 
 * Filters come in two forms: symbol filters and exchange filters.
 * 
 * Neue unbekannte Filter werden automatisch als UNKNOWN behandelt,
 * damit der Bot bei Binance API-Änderungen nicht crasht.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum FilterType {
  // Symbol Filters
  PRICE_FILTER,
  LOT_SIZE,
  MIN_NOTIONAL,
  NOTIONAL,
  MAX_NUM_ORDERS,
  MAX_ALGO_ORDERS,
  MAX_NUM_ALGO_ORDERS,
  ICEBERG_PARTS,
  PERCENT_PRICE,
  PERCENT_PRICE_BY_SIDE,
  MARKET_LOT_SIZE,
  MAX_NUM_ICEBERG_ORDERS,
  MAX_POSITION,
  TRAILING_DELTA,
  MAX_NUM_ORDER_LISTS,

  // Exchange Filters
  EXCHANGE_MAX_NUM_ORDERS,
  EXCHANGE_MAX_ALGO_ORDERS,
  EXCHANGE_MAX_NUM_ICEBERG_ORDERS,
  EXCHANGE_MAX_NUM_ORDER_LISTS,

  // Fallback für neue/unbekannte Filter-Typen
  @JsonEnumDefaultValue
  UNKNOWN
}