package com.a44dw.audiobookplayer;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class HelpCreditsFragment extends Fragment implements MainActivity.OnBackPressedListener,
                                                             View.OnClickListener{

    OnIterationWithActivityListener activityListener;

    public HelpCreditsFragment() {}


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FrameLayout holder = (FrameLayout) inflater.inflate(R.layout.fragment_help_credits, container, false);

        activityListener = (OnIterationWithActivityListener) getActivity();
        holder.findViewById(R.id.helpMailButton).setOnClickListener(this);
        holder.findViewById(R.id.helpStars).setOnClickListener(this);

        try {
            PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            TextView verNum = holder.findViewById(R.id.helpVersionNum);
            verNum.setText(pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return holder;
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case(R.id.helpMailButton): {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("message/rfc822");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"elevation1987@gmail.com"});
                intent.putExtra(Intent.EXTRA_SUBJECT, "Snail bookplayer");
                try {
                    startActivity(Intent.createChooser(intent, "Отправить через..."));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(getActivity(), "Не установлено ни одно почтовое приложение!", Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case(R.id.helpStars): {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=com.a44dw.audiobookplayer"));
                try {
                    startActivity(intent);
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(getActivity(), "Не установлен ни один браузер!", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        activityListener.goBack();
    }

}
