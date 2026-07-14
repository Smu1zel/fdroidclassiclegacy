package org.fdroid.fdroid.data;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Category implements Comparable<Category> {
    String name;
    private Context context;
    private int id;

    public Category(String name, int id, Context context) {
        this.name = name;
        this.id = id;
        this.context = context;
    }

    @Override
    public int compareTo(Category o) {
        return name.compareTo(o.name);
    }

    public String getTranslation() {
        Resources res = context.getResources();
        String pkgName = context.getPackageName();
        String resId = name.replace(" & ", "_").replace(" ", "_").replace("'", "");
        int id = res.getIdentifier("category_" + resId, "string", pkgName);
        return id == 0 ? name : context.getString(id);
    }

    @NonNull
    @Override
    public String toString() {
        return this.getTranslation();
    }

    @Override
    public boolean equals(@Nullable Object category) {
        if (category instanceof Category)
            return this.name.equals(((Category) category).name);
        else
            return false;
    }

    public int getId() {
        return id;
    }

    public void setId(int i) {
        id = i;
    }

    public String getName(){
        return name;
    }
}
