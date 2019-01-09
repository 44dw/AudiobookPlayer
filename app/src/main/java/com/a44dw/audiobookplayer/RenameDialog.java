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
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class RenameDialog extends DialogFragment {

    public static final String EXTRA_NAME = "name";
    public static final int RENAME_DIALOG_CODE = 1;
    public static final String RENAME_DIALOG_TAG = "bookmarkRenameDialog";

    LayoutInflater inflater;
    EditText newNameEditText;
    LinearLayout holder;

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        inflater = getActivity().getLayoutInflater();
        holder = (LinearLayout) inflater.inflate(R.layout.rename_dialog, null);
        newNameEditText = holder.findViewById(R.id.renameEditText);
        builder.setView(holder);
        builder.setTitle(R.string.rename_dialog_title);
        builder.setPositiveButton(R.string.dialog_positive, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_NAME, newNameEditText.getText().toString());
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

    @Override
    public void onResume() {
        super.onResume();
        AlertDialog d = (AlertDialog)getDialog();
        if(d != null) {
            final Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
            if(newNameEditText.getText().toString().length() == 0) positiveButton.setEnabled(false);
            newNameEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if(s.toString().trim().length() == 0) positiveButton.setEnabled(false);
                    else positiveButton.setEnabled(true);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

}
