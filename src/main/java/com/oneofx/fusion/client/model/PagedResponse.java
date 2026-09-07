package com.oneofx.fusion.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PagedResponse<T> {
    private List<T> data;
    private PageMeta meta;

    public List<T> getData() { return data == null ? Collections.emptyList() : data; }
    public void setData(List<T> data) { this.data = data; }
    public PageMeta getMeta() { return meta; }
    public void setMeta(PageMeta meta) { this.meta = meta; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMeta {
        private int limit;
        private boolean hasNextPage;
        private String nextCursor;

        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public boolean isHasNextPage() { return hasNextPage; }
        public void setHasNextPage(boolean hasNextPage) { this.hasNextPage = hasNextPage; }
        public String getNextCursor() { return nextCursor; }
        public void setNextCursor(String nextCursor) { this.nextCursor = nextCursor; }
    }
}
