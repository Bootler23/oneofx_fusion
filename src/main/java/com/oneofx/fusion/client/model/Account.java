package com.oneofx.fusion.client.model;

import java.util.Collections;
import java.util.List;

public class Account {
    private final List<AssetBalance> balances;

    public Account(List<AssetBalance> balances) {
        this.balances = balances == null ? Collections.emptyList() : List.copyOf(balances);
    }

    public List<AssetBalance> getBalances() {
        return balances;
    }

    public AssetBalance getAssetBalance(String symbol) {
        if (symbol == null) return null;
        return balances.stream()
                .filter(balance -> symbol.equalsIgnoreCase(balance.getAsset()))
                .findFirst()
                .orElse(null);
    }
}
