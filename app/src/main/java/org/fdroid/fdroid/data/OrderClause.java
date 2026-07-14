package org.fdroid.fdroid.data;

import androidx.annotation.NonNull;

class OrderClause {

    private final String expression;
    private String[] args;

    OrderClause(String expression) {
        this.expression = expression;
    }

    OrderClause(String field, String[] args, boolean isAscending) {
        this.expression = field + " " + (isAscending ? "ASC" : "DESC");
        this.args = args;
    }

    @NonNull
    @Override
    public String toString() {
        return expression;
    }

    public String[] getArgs() {
        return args;
    }
}
