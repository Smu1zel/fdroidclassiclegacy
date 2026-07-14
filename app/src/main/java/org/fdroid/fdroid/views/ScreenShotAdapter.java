package org.fdroid.fdroid.views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;

public class ScreenShotAdapter extends RecyclerView.Adapter<ScreenShotAdapter.ViewHolder> {
    private final DisplayImageOptions displayImageOptions;
    private final LayoutInflater inflater;
    private final String[] screenshotUrls;
    private ScreenshotClickListener clickListener;

    public ScreenShotAdapter(Context context, String[] screenshotUrls) {
        this.inflater = LayoutInflater.from(context);
        this.screenshotUrls = screenshotUrls;
        displayImageOptions = Utils.getDefaultDisplayImageOptionsBuilder()
                .showImageForEmptyUri(0).showImageOnFail(0).showImageOnLoading(0)
                .showImageOnFail(ResourcesCompat.getDrawable(context.getResources(),R.drawable.screenshot_placeholder,context.getTheme()))
                .showImageOnLoading(ResourcesCompat.getDrawable(context.getResources(),R.drawable.screenshot_placeholder,context.getTheme()))
                .showImageForEmptyUri(ResourcesCompat.getDrawable(context.getResources(),R.drawable.screenshot_placeholder,context.getTheme()))
                .build();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.screenshot_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = screenshotUrls[position];
        ImageLoader imageLoader = ImageLoader.getInstance();
        imageLoader.displayImage(url, holder.screenshot, displayImageOptions);
    }


    @Override
    public int getItemCount() {
        return screenshotUrls.length;
    }


    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        ImageView screenshot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            screenshot = itemView.findViewById(R.id.screenshot);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (clickListener != null) clickListener.onItemClick(view, getAdapterPosition());
        }
    }

    // allows clicks events to be caught
    public void setClickListener(ScreenshotClickListener itemClickListener) {
        this.clickListener = itemClickListener;
    }

    // parent activity will implement this method to respond to click events
    public interface ScreenshotClickListener {
        void onItemClick(View view, int position);
    }
}
