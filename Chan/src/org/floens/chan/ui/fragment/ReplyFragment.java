/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.fragment;

import java.io.File;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.manager.ReplyManager.ReplyResponse;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Reply;
import org.floens.chan.ui.ViewFlipperAnimations;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.utils.ImageDecoder;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.android.volley.Request.Method;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.StringRequest;
import com.micromobs.android.floatlabel.FloatLabelEditText;

public class ReplyFragment extends DialogFragment {
    private static final String TAG = "ReplyFragment";

    private int page = 0;

    private Loadable loadable;

    private final Reply draft = new Reply();
    private boolean shouldSaveDraft = true;

    private boolean gettingCaptcha = false;
    private String captchaChallenge = "";

    // Views
    private View container;
    private ViewFlipper flipper;
    private Button cancelButton;
    private Button fileButton;
    private Button fileDeleteButton;
    private Button submitButton;
    private FloatLabelEditText nameView;
    private FloatLabelEditText emailView;
    private FloatLabelEditText subjectView;
    private FloatLabelEditText commentView;
    private EditText fileNameView;
    private LoadView imageViewContainer;
    private LoadView captchaContainer;
    private TextView captchaInput;
    private LoadView responseContainer;

    private Activity context;

    public static ReplyFragment newInstance(Loadable loadable) {
        ReplyFragment reply = new ReplyFragment();
        reply.loadable = loadable;
        return reply;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        loadable.writeToBundle(context, outState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        context = getActivity();

        if (loadable == null && savedInstanceState != null) {
            loadable = new Loadable();
            loadable.readFromBundle(context, savedInstanceState);
        }

        if (loadable != null) {
            setClosable(true);

            Dialog dialog = getDialog();
            String title = loadable.isThreadMode() ? context.getString(R.string.reply) + " /" + loadable.board + "/"
                    + loadable.no : context.getString(R.string.reply_to_board) + " /" + loadable.board + "/";

            if (dialog == null) {
                context.getActionBar().setTitle(title);
            } else {
                dialog.setTitle(title);
            }

            if (getDialog() != null) {
                getDialog().setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            if (page == 1)
                                flipPage(0);
                            else if (page == 2)
                                closeReply();
                            return true;
                        } else
                            return false;
                    }
                });
            }

            Reply draft = ChanApplication.getReplyManager().getReplyDraft();

            if (TextUtils.isEmpty(draft.name)) {
                draft.name = ChanPreferences.getDefaultName();
            }

            if (TextUtils.isEmpty(draft.email)) {
                draft.email = ChanPreferences.getDefaultEmail();
            }

            nameView.getEditText().setText(draft.name);
            emailView.getEditText().setText(draft.email);
            subjectView.getEditText().setText(draft.subject);
            commentView.getEditText().setText(draft.comment);
            setFile(draft.fileName, draft.file);

            getCaptcha();
        } else {
            Logger.e(TAG, "Loadable in ReplyFragment was null");
            closeReply();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        ReplyManager replyManager = ChanApplication.getReplyManager();

        if (shouldSaveDraft) {
            draft.name = nameView.getText().toString();
            draft.email = emailView.getText().toString();
            draft.subject = subjectView.getText().toString();
            draft.comment = commentView.getText().toString();

            if (fileNameView != null) {
                draft.fileName = fileNameView.getText().toString();
            }

            replyManager.setReplyDraft(draft);
        } else {
            replyManager.removeReplyDraft();
            setFile(null, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ReplyManager replyManager = ChanApplication.getReplyManager();
        replyManager.removeFileListener();

        context = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // Setup the views with listeners
        container = inflater.inflate(R.layout.reply_view, null);
        flipper = (ViewFlipper) container.findViewById(R.id.reply_flipper);

        nameView = (FloatLabelEditText) container.findViewById(R.id.reply_name);
        emailView = (FloatLabelEditText) container.findViewById(R.id.reply_email);
        subjectView = (FloatLabelEditText) container.findViewById(R.id.reply_subject);
        commentView = (FloatLabelEditText) container.findViewById(R.id.reply_comment);
        imageViewContainer = (LoadView) container.findViewById(R.id.reply_image);
        responseContainer = (LoadView) container.findViewById(R.id.reply_response);
        captchaContainer = (LoadView) container.findViewById(R.id.reply_captcha_container);
        captchaContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getCaptcha();
            }
        });
        captchaInput = (TextView) container.findViewById(R.id.reply_captcha);

        if (ChanPreferences.getPassEnabled()) {
            ((TextView) container.findViewById(R.id.reply_captcha_text)).setText(R.string.pass_using);
            container.findViewById(R.id.reply_captcha_container).setVisibility(View.GONE);
            container.findViewById(R.id.reply_captcha).setVisibility(View.GONE);
        }

        cancelButton = (Button) container.findViewById(R.id.reply_cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (page == 1) {
                    flipPage(0);
                } else {
                    closeReply();
                }
            }
        });

        fileButton = (Button) container.findViewById(R.id.reply_file);
        fileButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ChanApplication.getReplyManager().pickFile(new ReplyManager.FileListener() {
                    @Override
                    public void onFile(String name, File file) {
                        setFile(name, file);
                    }

                    @Override
                    public void onFileLoading() {
                        imageViewContainer.setView(null);
                    }
                });
            }
        });

        fileDeleteButton = (Button) container.findViewById(R.id.reply_file_delete);
        fileDeleteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setFile(null, null);
            }
        });

        submitButton = (Button) container.findViewById(R.id.reply_submit);
        submitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (page == 0) {
                    flipPage(1);
                } else if (page == 1) {
                    flipPage(2);
                    submit();
                }
                ;
            }
        });

        return container;
    }

    private void closeReply() {
        if (getDialog() != null) {
            dismiss();
        } else {
            context.finish();
        }
    }

    /**
     * Set if the dialog is able to be closed, by pressing outside of the
     * dialog, or something else.
     */
    private void setClosable(boolean e) {
        if (getDialog() != null) {
            getDialog().setCanceledOnTouchOutside(e);
            setCancelable(e);
        }
    }

    /**
     * Flip to an page with an animation. Sets the correct text on the
     * cancelButton:
     * 
     * @param position
     *            0-2
     */
    private void flipPage(int position) {
        boolean flipBack = position < page;

        page = position;

        if (flipBack) {
            flipper.setInAnimation(ViewFlipperAnimations.BACK_IN);
            flipper.setOutAnimation(ViewFlipperAnimations.BACK_OUT);
            flipper.showPrevious();
        } else {
            flipper.setInAnimation(ViewFlipperAnimations.NEXT_IN);
            flipper.setOutAnimation(ViewFlipperAnimations.NEXT_OUT);
            flipper.showNext();
        }

        if (page == 0) {
            cancelButton.setText(R.string.cancel);
        } else if (page == 1) {
            cancelButton.setText(R.string.back);
        } else if (page == 2) {
            cancelButton.setText(R.string.close);
        }
    }

    /**
     * Set the picked image in the imageView. Sets the file in the draft. Call
     * null on the file to empty the imageView.
     * 
     * @param imagePath
     *            file to image to send or null to clear
     */
    private void setFile(final String name, final File file) {
        draft.file = file;
        draft.fileName = name;

        if (file == null) {
            fileDeleteButton.setEnabled(false);
            imageViewContainer.removeAllViews();
            fileNameView = null;
        } else {
            fileDeleteButton.setEnabled(true);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (context == null)
                        return;

                    final Bitmap bitmap = ImageDecoder.decodeFile(file, imageViewContainer.getWidth(), 3000);

                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (context == null)
                                return;

                            if (bitmap == null) {
                                Toast.makeText(context, R.string.image_preview_failed, Toast.LENGTH_LONG).show();
                            } else {
                                LinearLayout wrapper = new LinearLayout(context);
                                wrapper.setLayoutParams(Utils.MATCH_WRAP_PARAMS);
                                wrapper.setOrientation(LinearLayout.VERTICAL);

                                fileNameView = new EditText(context);
                                fileNameView.setSingleLine();
                                fileNameView.setHint(R.string.reply_file_name);
                                fileNameView.setTextSize(16f);
                                fileNameView.setText(name);
                                wrapper.addView(fileNameView);

                                ImageView imageView = new ImageView(context);
                                imageView.setScaleType(ScaleType.CENTER_INSIDE);
                                imageView.setImageBitmap(bitmap);
                                wrapper.addView(imageView);

                                imageViewContainer.setView(wrapper);
                            }
                        }
                    });
                }
            }).start();
        }
    }

    private void getCaptcha() {
        if (gettingCaptcha)
            return;
        gettingCaptcha = true;

        captchaContainer.setView(null);

        String url = ChanUrls.getCaptchaChallengeUrl();

        ChanApplication.getVolleyRequestQueue().add(new StringRequest(Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String result) {
                if (context != null) {
                    String challenge = ReplyManager.getChallenge(result);
                    if (challenge != null) {
                        captchaChallenge = challenge;
                        String imageUrl = ChanUrls.getCaptchaImageUrl(challenge);

                        NetworkImageView captchaImage = new NetworkImageView(context);
                        captchaImage.setImageUrl(imageUrl, ChanApplication.getImageLoader());
                        captchaContainer.setView(captchaImage);

                        gettingCaptcha = false;
                    }
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                gettingCaptcha = false;

                if (context != null) {
                    TextView text = new TextView(context);
                    text.setGravity(Gravity.CENTER);
                    text.setText(R.string.reply_captcha_load_error);
                    captchaContainer.setView(text);
                }
            }
        }));
    }

    /**
     * Submit button clicked at page 1
     */
    private void submit() {
        submitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        setClosable(false);

        responseContainer.setView(null);

        draft.name = nameView.getText().toString();
        draft.email = emailView.getText().toString();
        draft.subject = subjectView.getText().toString();
        draft.comment = commentView.getText().toString();
        draft.captchaChallenge = captchaChallenge;
        draft.captchaResponse = captchaInput.getText().toString();

        draft.fileName = "image";
        if (fileNameView != null) {
            String n = fileNameView.getText().toString();
            if (!TextUtils.isEmpty(n)) {
                draft.fileName = n;
            }
        }

        draft.resto = loadable.isBoardMode() ? -1 : loadable.no;
        draft.board = loadable.board;

        if (ChanPreferences.getPassEnabled()) {
            draft.usePass = true;
            draft.passId = ChanPreferences.getPassId();
        }

        ChanApplication.getReplyManager().sendReply(draft, new ReplyManager.ReplyListener() {
            @Override
            public void onResponse(ReplyResponse response) {
                handleSubmitResponse(response);
            }
        });
    }

    /**
     * Got response about or reply from ReplyManager
     * 
     * @param response
     */
    private void handleSubmitResponse(ReplyResponse response) {
        if (context == null)
            return;

        if (response.isNetworkError || response.isUserError) {
            int resId = response.isCaptchaError ? R.string.reply_error_captcha
                    : (response.isFileError ? R.string.reply_error_file : R.string.reply_error);
            Toast.makeText(context, resId, Toast.LENGTH_LONG).show();
            submitButton.setEnabled(true);
            cancelButton.setEnabled(true);
            setClosable(true);
            flipPage(1);
            getCaptcha();
            captchaInput.setText("");
        } else if (response.isSuccessful) {
            shouldSaveDraft = false;
            Toast.makeText(context, R.string.reply_success, Toast.LENGTH_SHORT).show();
            //            threadFragment.reload(); // won't work: it takes 4chan a variable time to process the reply
            closeReply();
        } else {
            cancelButton.setEnabled(true);
            setClosable(true);

            WebView webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            settings.setSupportZoom(true);

            webView.loadData(response.responseData, "text/html", null);

            responseContainer.setView(webView);
        }
    }
}
