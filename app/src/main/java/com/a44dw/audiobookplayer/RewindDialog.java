package com.a44dw.audiobookplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RewindDialog extends DialogFragment {

    public static final String EXTRA_SEC = "extraSec";
    public static final String REWIND_DIALOG_TAG = "rewindDialog";
    public static final int REWIND_DIALOG_CODE = 4;

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        String nowRewind = getArguments().getString(EXTRA_SEC);
        LinearLayout holder = (LinearLayout) inflater.inflate(R.layout.rewind_dialog, null);
        builder.setView(holder);
        builder.setTitle(R.string.rewind_title);

        Resources res = getResources();
        final String[] secs = res.getStringArray(R.array.pref_valies_rewind);

        for(int i=0; i<holder.getChildCount(); i++) {
            String s = secs[i];
            String text = s + " сек.";
            TextView tv = (TextView) holder.getChildAt(i);
            if(s.equals(nowRewind)) tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setText(text);
            tv.setTag(s);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.putExtra(EXTRA_SEC, (String) v.getTag());
                    getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
                    dismiss();
                }
            });
        }

        builder.setNegativeButton(R.string.dialog_negative, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_CANCELED, getActivity().getIntent());
            }
        });
        return builder.create();
    }
}
