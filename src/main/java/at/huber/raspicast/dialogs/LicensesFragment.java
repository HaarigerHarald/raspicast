/**
 * Copyright 2013 Adam Speakman

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package at.huber.raspicast.dialogs;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import at.huber.raspicast.R;

//TODO If you don't support Android 2.x, you should use the non-support version!

/**
 * Created by Adam Speakman on 24/09/13.
 * http://speakman.net.nz
 */
public class LicensesFragment extends DialogFragment {

    private AsyncTask<Void, Void, String> mLicenseLoader;

    private static final String FRAGMENT_TAG = "nz.net.speakman.androidlicensespage.LicensesFragment";
    private static final String KEY_SHOW_CLOSE_BUTTON = "keyShowCloseButton";

    /**
     * Creates a new instance of LicensesFragment with no Close button.
     *
     * @return A new licenses fragment.
     */
    public static LicensesFragment newInstance() {
        return new LicensesFragment();
    }

    /**
     * Creates a new instance of LicensesFragment with an optional Close button.
     *
     * @param showCloseButton Whether to show a Close button at the bottom of the dialog.
     *
     * @return A new licenses fragment.
     */
    public static LicensesFragment newInstance(boolean showCloseButton) {
        LicensesFragment fragment = new LicensesFragment();

        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_SHOW_CLOSE_BUTTON, showCloseButton);
        fragment.setArguments(bundle);

        return fragment;
    }

    /**
     * Builds and displays a licenses fragment with no Close button. Requires
     * "/res/raw/licenses.html" and "/res/layout/licenses_fragment.xml" to be
     * present.
     *
     * @param fm A fragment manager instance used to display this LicensesFragment.
     */
    public static void displayLicensesFragment(FragmentManager fm) {
        FragmentTransaction ft = fm.beginTransaction();
        Fragment prev = fm.findFragmentByTag(FRAGMENT_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment newFragment = LicensesFragment.newInstance();
        newFragment.show(ft, FRAGMENT_TAG);
    }

    /**
     * Builds and displays a licenses fragment with or without a Close button.
     * Requires "/res/raw/licenses.html" and "/res/layout/licenses_fragment.xml"
     * to be present.
     *
     * @param fm A fragment manager instance used to display this LicensesFragment.
     * @param showCloseButton Whether to show a Close button at the bottom of the dialog.
     */
    public static void displayLicensesFragment(FragmentManager fm, boolean showCloseButton) {
        FragmentTransaction ft = fm.beginTransaction();
        Fragment prev = fm.findFragmentByTag(FRAGMENT_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment newFragment = LicensesFragment.newInstance(showCloseButton);
        newFragment.show(ft, FRAGMENT_TAG);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadLicenses();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLicenseLoader != null) {
            mLicenseLoader.cancel(true);
        }
    }

    private WebView mWebView;
    private ProgressBar mIndeterminateProgress;

    @NonNull
    @SuppressLint("InflateParams")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View content = LayoutInflater.from(getActivity()).inflate(R.layout.licenses_fragment, null);
        mWebView = (WebView) content.findViewById(R.id.licensesFragmentWebView);
        mIndeterminateProgress = (ProgressBar) content.findViewById(R.id.licensesFragmentIndeterminateProgress);

        boolean showCloseButton = false;
        Bundle arguments = getArguments();
        if (arguments != null) {
            showCloseButton = arguments.getBoolean(KEY_SHOW_CLOSE_BUTTON);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Open Source licenses");
        builder.setView(content);
        if (showCloseButton) {
            builder.setNegativeButton("Close",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        }
        
        Dialog d= builder.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
			
			@Override
			public void onShow(DialogInterface dialog) {
				WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
			    lp.copyFrom(((Dialog) dialog).getWindow().getAttributes());
			    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
			    lp.height = WindowManager.LayoutParams.MATCH_PARENT;
			    ((Dialog) dialog).getWindow().setAttributes(lp);
			}
		});

        return d;
    }

    private void loadLicenses() {
        // Load asynchronously in case of a very large file.
        mLicenseLoader = new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... params) {
                InputStream rawResource = getActivity().getResources().openRawResource(R.raw.licenses);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(rawResource));

                String line;
                StringBuilder sb = new StringBuilder();

                try {
                    while ((line = bufferedReader.readLine()) != null) {
                        sb.append(line);
                        sb.append("\n");
                    }
                    bufferedReader.close();
                } catch (IOException e) {
                    // TODO You may want to include some logging here.
                }

                return sb.toString();
            }

            @Override
            protected void onPostExecute(String licensesBody) {
                super.onPostExecute(licensesBody);
                if (getActivity() == null || isCancelled()) {
                    return;
                }
                mIndeterminateProgress.setVisibility(View.INVISIBLE);
                mWebView.setVisibility(View.VISIBLE);
                mWebView.loadDataWithBaseURL(null, licensesBody, "text/html", "utf-8", null);
                mLicenseLoader = null;
            }

        }.execute();
    }
}
