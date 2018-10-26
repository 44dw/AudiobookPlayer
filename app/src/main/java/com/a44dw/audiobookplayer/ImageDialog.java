package com.a44dw.audiobookplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class ImageDialog extends DialogFragment {

    public static final String IMAGE_DIALOG_TAG = "imageRenameDialog";
    public static final String BUNDLE_IMAGE = "image";

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        LinearLayout holder = (LinearLayout) inflater.inflate(R.layout.image_dialog, null);
        byte[] art = (byte[]) getArguments().getSerializable(BUNDLE_IMAGE);
        Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);

        ImageView view = holder.findViewById(R.id.imageDialogView);
        view.setImageBitmap(bitmap);
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        builder.setView(holder);
        return builder.create();
    }
}
