package com.a44dw.audiobookplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class SpeedDialog extends DialogFragment {

    public static final String EXTRA_SPEED = "extraSpeed";
    public static final String SPEED_DIALOG_TAG = "speedDialog";
    public static final int SPEED_DIALOG_CODE = 3;

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        float nowSpeed = getArguments().getFloat(EXTRA_SPEED);
        TableLayout holder = (TableLayout) inflater.inflate(R.layout.speed_dialog, null);
        builder.setView(holder);
        builder.setTitle(R.string.speed_title);
        float speedValue = 0.5f;
        for(int i=0; i<holder.getChildCount(); i++) {
            TableRow row = (TableRow) holder.getChildAt(i);
            for(int k=0; k<row.getChildCount(); k++) {
                TextView tv = (TextView) row.getChildAt(k);
                tv.setTag(speedValue);
                if(speedValue == nowSpeed) tv.setTypeface(Typeface.DEFAULT_BOLD);
                row.getChildAt(k).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent();
                        intent.putExtra(EXTRA_SPEED, (float)v.getTag());
                        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
                        dismiss();
                    }
                });
                speedValue += 0.25f;
            }
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
