package org.fdroid.fdroid.data;

import android.content.Context;

public class RepoCategory extends Category {
    public Repo repo;

    public RepoCategory(Repo repo, int id, Context context) {
        super(repo.name, id, context);
        this.repo = repo;
    }

    @Override
    public String getTranslation() {
        return name;
    }
}
