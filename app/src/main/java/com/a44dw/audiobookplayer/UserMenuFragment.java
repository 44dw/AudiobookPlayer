package com.a44dw.audiobookplayer;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class UserMenuFragment extends Fragment implements View.OnClickListener {

    OnIterationWithActivityListener mActivityListener;

    public UserMenuFragment() {}

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);

        if (c instanceof OnIterationWithActivityListener) {
            mActivityListener = (OnIterationWithActivityListener) c;
        } else {
            throw new RuntimeException(c.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConstraintLayout holder = (ConstraintLayout) inflater.inflate(R.layout.fragment_user_menu, container, false);
        TextView usermenuLastbooks = holder.findViewById(R.id.usermenuLastbooks);
        TextView usermenuFileManager = holder.findViewById(R.id.usermenuFileManager);
        usermenuFileManager.setOnClickListener(this);
        usermenuLastbooks.setOnClickListener(this);

        if(!mActivityListener.hasBooksInStorage())
            usermenuLastbooks.setVisibility(View.GONE);

        return holder;
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.usermenuFileManager: {
                mActivityListener.showFileManager(true);
                break;
            }
            case R.id.usermenuLastbooks: {
                mActivityListener.showLastBooks(true);
                break;
            }
        }
    }
}
