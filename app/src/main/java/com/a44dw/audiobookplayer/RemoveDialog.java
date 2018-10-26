package com.a44dw.audiobookplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class RemoveDialog extends DialogFragment {

    public static final int REMOVE_DIALOG_CODE = 2;
    public static final String EXTRA_DEL_FILES = "delFiles";
    public static final String BUNDLE_DEL_FILES = "delFiles";
    public static final String REMOVE_DIALOG_TAG = "bookmarkRemoveDialog";

    LayoutInflater inflater;
    LinearLayout holder;
    CheckBox checkbox;
    //TextView bookName;
    TextView warning;

    public RemoveDialog() {}

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        ArrayList<String> names = getArguments().getStringArrayList(BUNDLE_DEL_FILES);
        inflater = getActivity().getLayoutInflater();
        holder = (LinearLayout) inflater.inflate(R.layout.remove_dialog, null);
        checkbox = holder.findViewById(R.id.removeDialogCheckbox);
        warning = holder.findViewById(R.id.removeDialogWarning);
        checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) warning.setVisibility(View.VISIBLE);
                else warning.setVisibility(View.GONE);
            }
        });
        for(String name : names) {
            Log.d(MainActivity.TAG, "RemoveDialog -> onCreateDialog(): name is " + name);
            TextView nameView = new TextView(getContext());
            nameView.setText(name);
            holder.addView(nameView,0);
        }
        builder.setView(holder);
        builder.setTitle(R.string.remove_dialog_title);
        builder.setPositiveButton(R.string.dialog_positive, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_DEL_FILES, checkbox.isChecked());
                getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
            }
        });
        builder.setNegativeButton(R.string.dialog_negative, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_CANCELED, getActivity().getIntent());
            }
        });
        return builder.create();
    }
}
