package com.a44dw.audiobookplayer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

public class ImageDialog extends DialogFragment {

    public static final String IMAGE_DIALOG_TAG = "imageRenameDialog";
    public static final String BUNDLE_IMAGE = "image";

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        @NonNull LayoutInflater inflater = getActivity().getLayoutInflater();
        ImageView imageView = (ImageView) inflater.inflate(R.layout.image_dialog, null);
        byte[] art = (byte[]) getArguments().getSerializable(BUNDLE_IMAGE);
        Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
        imageView.setImageBitmap(bitmap);
        imageView.setClickable(true);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        builder.setView(imageView);

        Dialog dialog = builder.create();
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
