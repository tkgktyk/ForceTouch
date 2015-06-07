/*
 * Copyright 2015 Takagi Katsuyuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.tkgktyk.xposed.forcetouchdetector.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

/**
 * Created by tkgktyk on 2015/04/16.
 */
public class ConfirmDialogFragment extends DialogFragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_POSITIVE = "positive";
    private static final String ARG_NEGATIVE = "negative";

    public ConfirmDialogFragment() {
    }

    public static ConfirmDialogFragment newInstance(String title, String message,
                                                    String positive, String negative,
                                                    Bundle extras, int requestCode) {
        return newInstance(title, message, positive, negative, extras, null, requestCode);
    }

    public static ConfirmDialogFragment newInstance(String title, String message,
                                                    String positive, String negative,
                                                    Bundle extras, Fragment target,
                                                    int requestCode) {
        Bundle args = extras;
        if (args == null) {
            args = new Bundle();
        }
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSITIVE, positive);
        args.putString(ARG_NEGATIVE, negative);

        ConfirmDialogFragment fragment = new ConfirmDialogFragment();
        fragment.setArguments(args);
        fragment.setTargetFragment(target, requestCode);
        return fragment;
    }

    private String getTitle() {
        return getArguments().getString(ARG_TITLE);
    }

    private String getMessage() {
        return getArguments().getString(ARG_MESSAGE);
    }

    private String getPositive() {
        return getArguments().getString(ARG_POSITIVE);
    }

    private String getNegative() {
        return getArguments().getString(ARG_NEGATIVE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(getTitle())
                .setMessage(getMessage())
                .setPositiveButton(getPositive(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        returnToTarget();
                    }
                })
                .setNegativeButton(getNegative(), null)
                .create();
    }

    protected void returnToTarget() {
        OnConfirmedListener listener;
        Fragment target = getTargetFragment();
        if (target != null) {
            listener = (OnConfirmedListener) target;
        } else {
            listener = (OnConfirmedListener) getActivity();
        }
        listener.onConfirmed(getTargetRequestCode(), getArguments());
    }

    public interface OnConfirmedListener {
        void onConfirmed(int requestCode, Bundle extras);
    }
}
