package com.lunartag.app.ui.viewer;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.lunartag.app.R;

import java.io.File;
import java.util.List;

public class ImageViewerAdapter extends RecyclerView.Adapter<ImageViewerAdapter.ViewerHolder> {

    private final Context context;
    private final List<String> imagePaths;

    public ImageViewerAdapter(Context context, List<String> imagePaths) {
        this.context = context;
        this.imagePaths = imagePaths;
    }

    @NonNull
    @Override
    public ViewerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_viewer, parent, false);
        return new ViewerHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewerHolder holder, int position) {
        String path = imagePaths.get(position);

        // FIXED: Handle Custom Folder (Content URI) vs Standard File
        if (path != null && path.startsWith("content://")) {
            // It is a Custom Folder URI - Load directly via Glide
            Glide.with(context)
                    .load(Uri.parse(path))
                    .into(holder.imageView);
        } else {
            // It is a Standard Internal File
            File file = new File(path);

            // Load the image using Glide. 
            // Note: We do NOT downsample here (no .override) because the user wants to see details.
            if (file.exists()) {
                Glide.with(context)
                        .load(file)
                        .into(holder.imageView);
            }
        }
    }

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    static class ViewerHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        ViewerHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_full_screen);
        }
    }
}