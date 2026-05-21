package com.example.moderationapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.PostEngagementDAO;
import com.example.moderationapp.data.dao.RandomContentGenerator;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.MessageReports;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.PostEngagement;
import com.example.moderationapp.data.model.Report;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.data.persistence.DataManager;
import com.example.moderationapp.data.persistence.io.AndroidIOFactory;
import com.example.moderationapp.logic.engagement.PostEngagementService;
import com.example.moderationapp.logic.moderation.ModerationTools;
import com.example.moderationapp.logic.util.TimeFormatter;
import com.example.moderationapp.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String CHANNEL_REPLIES = "reply_notifications_red_badge";
    private static final String EXTRA_POST_ID = "post_id";
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int UNREAD_BADGE_RED = 0xFFE53935;

    private FrameLayout root;
    private User currentUser;
    private String adminSortMode = "PRIORITY";
    private final DataManager dataManager = DataManager.getInstance();
    private final PostEngagementService engagementService = PostEngagementService.getInstance();
    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private UUID pendingPostId;
    private boolean inForeground;
    private View bottomNavBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataManager.configureIO(new AndroidIOFactory(getApplicationContext()));
        pendingPostId = postIdFromIntent(getIntent());
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        root = new FrameLayout(this);
        setContentView(root);

        // Load data in background
        diskExecutor.execute(() -> {
            dataManager.readAll();
            seedDemoDataIfEmpty();
            if (engagementService.ensureMetadataForExistingPosts()) {
                dataManager.writeAll();
            }
            runOnUiThread(this::showLogin);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingPostId = postIdFromIntent(intent);
        if (currentUser != null && showPendingPostIfAny()) return;
        if (currentUser != null) showPostList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        inForeground = true;
        notificationHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        inForeground = false;
        scheduleDemoReplyNotification();
    }

    private void saveInBackground(Runnable onDone) {
        diskExecutor.execute(() -> {
            dataManager.writeAll();
            if (onDone != null) runOnUiThread(onDone);
        });
    }

    private void showLogin() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.parseColor("#F9FCFF"));

        LinearLayout brandArea = new LinearLayout(this);
        brandArea.setOrientation(LinearLayout.VERTICAL);
        brandArea.setGravity(Gravity.CENTER_HORIZONTAL);
        brandArea.setPadding(dp(24), dp(56), dp(24), dp(28));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(100), dp(100));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        brandArea.addView(logo, logoParams);

        TextView appName = new TextView(this);
        appName.setText("AnuShare");
        appName.setTextSize(14);
        appName.setTextColor(Color.rgb(93, 102, 117));
        appName.setPadding(0, dp(8), 0, 0);
        appName.setGravity(Gravity.CENTER);
        applyTimes(appName, Typeface.NORMAL);
        brandArea.addView(appName);
        content.addView(brandArea);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(28));

        TextView heading = new TextView(this);
        heading.setText("Sign in");
        heading.setTextSize(28);
        heading.setTextColor(Color.parseColor("#081F5C"));
        heading.setPadding(0, 0, 0, dp(16));
        applyTimes(heading, Typeface.BOLD);
        form.addView(heading);

        form.addView(sectionLabel("USERNAME"));
        EditText username = input("Enter username", false);
        form.addView(username);
        form.addView(gap(8));

        form.addView(sectionLabel("PASSWORD"));
        EditText password = input("Enter password", true);
        form.addView(password);
        form.addView(gap(20));

        Button signIn = primaryButton("Sign in");
        signIn.setOnClickListener(v -> {
            User user = UserDAO.getInstance().login(username.getText().toString().trim(), password.getText().toString());
            if (user == null) {
                toast("Invalid username or password");
                return;
            }
            currentUser = user;
            if (showPendingPostIfAny()) return;
            showPostList();
        });
        form.addView(signIn);

        Button register = secondaryButton("Create account");
        register.setOnClickListener(v -> showRegister());
        form.addView(register);

        content.addView(form);
        scrollView.addView(content);
        root.removeAllViews();
        root.addView(scrollView);
    }

    private void showRegister() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.parseColor("#F9FCFF"));

        LinearLayout brandArea = new LinearLayout(this);
        brandArea.setOrientation(LinearLayout.VERTICAL);
        brandArea.setGravity(Gravity.CENTER_HORIZONTAL);
        brandArea.setPadding(dp(24), dp(48), dp(24), dp(20));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(80), dp(80));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        brandArea.addView(logo, logoParams);

        TextView appName = new TextView(this);
        appName.setText("AnuShare");
        appName.setTextSize(14);
        appName.setTextColor(Color.rgb(93, 102, 117));
        appName.setPadding(0, dp(8), 0, 0);
        appName.setGravity(Gravity.CENTER);
        applyTimes(appName, Typeface.NORMAL);
        brandArea.addView(appName);
        content.addView(brandArea);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(28));

        TextView heading = new TextView(this);
        heading.setText("Create account");
        heading.setTextSize(24);
        heading.setTextColor(Color.parseColor("#081F5C"));
        heading.setPadding(0, 0, 0, dp(16));
        applyTimes(heading, Typeface.BOLD);
        form.addView(heading);

        form.addView(sectionLabel("USERNAME"));
        EditText username = input("Enter username", false);
        form.addView(username);
        form.addView(gap(8));

        form.addView(sectionLabel("PASSWORD"));
        EditText password = input("Enter password", true);
        form.addView(password);
        form.addView(gap(20));

        Button create = primaryButton("Register");
        create.setOnClickListener(v -> {
            User user = UserDAO.getInstance().register(username.getText().toString().trim(), password.getText().toString());
            if (user == null) {
                toast("Username or password is not valid");
                return;
            }
            currentUser = user;
            saveInBackground(() -> {
                if (!showPendingPostIfAny()) showPostList();
            });
        });
        form.addView(create);

        Button back = secondaryButton("Back to sign in");
        back.setOnClickListener(v -> showLogin());
        form.addView(back);

        content.addView(form);
        scrollView.addView(content);
        root.removeAllViews();
        root.addView(scrollView);
    }

    private void showPostList() {
        boolean isAdmin = currentUser.role() == User.Role.Admin;

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.parseColor("#F9FCFF"));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        scrollView.setLayoutParams(scrollParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(124));
        content.setBackgroundColor(Color.parseColor("#F9FCFF"));

        content.addView(postListHeader());

        LinearLayout listSection = new LinearLayout(this);
        listSection.setOrientation(LinearLayout.VERTICAL);
        listSection.setPadding(dp(20), dp(18), dp(20), 0);

        if (isAdmin) {
            listSection.addView(adminListIntroCard());

            Button admin = detailActionButton("Open Admin Dashboard", Color.parseColor("#081F5C"));
            admin.setOnClickListener(v -> showAdminPanel());
            LinearLayout.LayoutParams adminParams = blockParams();
            adminParams.setMargins(0, 0, 0, dp(16));
            admin.setLayoutParams(adminParams);
            listSection.addView(admin);
        }

        List<Post> posts = new ArrayList<>();
        for (Iterator<Post> it = PostDAO.getInstance().getAll(); it.hasNext(); ) {
            Post item = it.next();
            if (canViewPost(item)) posts.add(item);
        }
        posts.sort((left, right) -> Long.compare(postTimestamp(right, isAdmin), postTimestamp(left, isAdmin)));

        if (posts.isEmpty()) {
            TextView empty = bodyText("No posts yet. Tap the button below to create the first one.");
            applyTimes(empty, Typeface.NORMAL);
            empty.setPadding(0, dp(40), 0, 0);
            listSection.addView(empty);
        } else {
            for (Post post : posts) {
                listSection.addView(postCard(post, isAdmin, this::showPostList));
            }
        }

        content.addView(listSection);

        scrollView.addView(content);
        screen.addView(scrollView);
        screen.addView(postComposerDock(false, null));

        root.removeAllViews();
        root.addView(screen);
    }

    private View adminListIntroCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(reportCardBackground(Color.parseColor("#D0E3FF")));

        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText("Admin View");
        title.setTextSize(17);
        title.setTextColor(Color.parseColor("#081F5C"));
        applyTimes(title, Typeface.BOLD);
        card.addView(title);

        TextView body = new TextView(this);
        body.setText("Reported posts are highlighted for admin review.");
        body.setTextSize(12);
        body.setTextColor(Color.parseColor("#213666"));
        body.setPadding(0, dp(4), 0, 0);
        body.setSingleLine(true);
        body.setEllipsize(TextUtils.TruncateAt.END);
        applyTimes(body, Typeface.NORMAL);
        card.addView(body);

        return card;
    }

    private void showCreatePost() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.parseColor("#F9FCFF"));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.parseColor("#F9FCFF"));
        content.setPadding(0, 0, 0, dp(24));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(20), dp(20), dp(20), dp(20));
        topBar.setBackgroundColor(Color.parseColor("#7096D1"));

        Button cancel = textActionButton("Cancel");
        applyTimes(cancel, Typeface.NORMAL);
        cancel.setTextColor(Color.WHITE);
        cancel.setMinWidth(dp(104));
        cancel.setMinimumWidth(dp(104));
        cancel.setMinHeight(dp(48));
        cancel.setMinimumHeight(dp(48));
        cancel.setOnClickListener(v -> showPostList());
        topBar.addView(cancel);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
        title.setLayoutParams(titleParams);
        title.setGravity(Gravity.CENTER);
        title.setText("Create post");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(9, 42, 122));
        applyTimes(title, Typeface.BOLD);
        topBar.addView(title);

        Button post = new Button(this);
        post.setAllCaps(false);
        post.setText("Post");
        post.setTextSize(15);
        post.setMinHeight(0);
        post.setMinimumHeight(0);
        post.setPadding(dp(18), dp(10), dp(18), dp(10));
        post.setMinWidth(dp(104));
        post.setMinimumWidth(dp(104));
        post.setMinHeight(dp(48));
        post.setMinimumHeight(dp(48));
        post.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        applyTimes(post, Typeface.BOLD);
        setActionButtonEnabled(post, false);
        topBar.addView(post);

        LinearLayout authorRow = new LinearLayout(this);
        authorRow.setOrientation(LinearLayout.HORIZONTAL);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        authorRow.setPadding(dp(26), dp(18), dp(26), dp(18));

        TextView createAvatar = avatarView(currentUser, currentUser.username(), 32);
        LinearLayout.LayoutParams createAvatarParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        authorRow.addView(createAvatar, createAvatarParams);

        TextView username = new TextView(this);
        username.setText(currentUser.username());
        username.setTextSize(14);
        username.setTextColor(Color.rgb(18, 35, 76));
        applyTimes(username, Typeface.BOLD);
        LinearLayout.LayoutParams createNameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        createNameParams.setMargins(dp(10), 0, 0, 0);
        authorRow.addView(username, createNameParams);

        LinearLayout formSection = new LinearLayout(this);
        formSection.setOrientation(LinearLayout.VERTICAL);
        formSection.setPadding(dp(26), dp(24), dp(26), dp(28));
        final Set<PostEngagement.Tag> selectedTags = new LinkedHashSet<>();
        selectedTags.add(PostEngagement.Tag.ACADEMIC);
        final PostEngagement.Visibility[] selectedVisibility =
                new PostEngagement.Visibility[] {PostEngagement.Visibility.PUBLIC};

        TextView titleLabel = sectionLabel("TITLE");
        formSection.addView(titleLabel);

        EditText postTitle = new EditText(this);
        postTitle.setHint("An interesting title");
        postTitle.setSingleLine(true);
        postTitle.setTextSize(16);
        postTitle.setTextColor(Color.rgb(21, 23, 26));
        postTitle.setHintTextColor(Color.parseColor("#E7F1FF"));
        postTitle.setBackground(inputFieldBackground());
        postTitle.setPadding(dp(20), dp(16), dp(20), dp(16));
        enableClipboard(postTitle);
        applyTimes(postTitle, Typeface.NORMAL);
        LinearLayout.LayoutParams titleInputParams = blockParams();
        titleInputParams.setMargins(0, dp(10), 0, dp(24));
        postTitle.setLayoutParams(titleInputParams);
        formSection.addView(postTitle);

        formSection.addView(sectionLabel("TAGS"));
        formSection.addView(tagPicker(selectedTags));

        formSection.addView(sectionLabel("BODY"));

        EditText postContent = new EditText(this);
        postContent.setHint("Share more details, ask a question, or start a discussion......");
        postContent.setHintTextColor(Color.parseColor("#E7F1FF"));
        postContent.setTextSize(15);
        postContent.setTextColor(Color.rgb(21, 23, 26));
        postContent.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        postContent.setSingleLine(false);
        postContent.setMinLines(7);
        postContent.setGravity(Gravity.TOP | Gravity.START);
        postContent.setBackground(inputFieldBackground());
        postContent.setPadding(dp(20), dp(16), dp(20), dp(16));
        enableClipboard(postContent);
        applyTimes(postContent, Typeface.NORMAL);
        LinearLayout.LayoutParams contentParams = blockParams();
        contentParams.setMargins(0, dp(10), 0, dp(26));
        postContent.setLayoutParams(contentParams);
        formSection.addView(postContent);
        formSection.addView(collapsibleVisibilityPicker(selectedVisibility));

        content.addView(topBar);
        content.addView(dividerLine());
        content.addView(authorRow);
        content.addView(dividerLine());
        content.addView(formSection);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean ready = !postTitle.getText().toString().trim().isEmpty()
                        && !postContent.getText().toString().trim().isEmpty();
                setActionButtonEnabled(post, ready);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        postTitle.addTextChangedListener(watcher);
        postContent.addTextChangedListener(watcher);

        post.setOnClickListener(v -> {
            String topic = postTitle.getText().toString().trim();
            String body = postContent.getText().toString().trim();
            if (topic.isEmpty() || body.isEmpty()) {
                toast("Please enter a title and content");
                return;
            }

            Post newPost = new Post(UUID.randomUUID(), currentUser.id(), topic);
            Message openingMessage = new Message(
                    UUID.randomUUID(),
                    currentUser.id(),
                    newPost.id,
                    System.currentTimeMillis(),
                    body);

            boolean addedPost = PostDAO.getInstance().add(newPost);
            boolean addedMessage = newPost.messages.insert(openingMessage);
            if (!addedPost || !addedMessage) {
                toast("Unable to create post");
                return;
            }
            engagementService.updatePostSettings(newPost.id, selectedTags, selectedVisibility[0]);

            saveInBackground(() -> {
                toast("Post created");
                showPostList();
            });
        });

        scrollView.addView(content);
        root.removeAllViews();
        root.addView(scrollView);
    }

    private void showPostDetail(Post post) {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.parseColor("#F9FCFF"));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        scrollView.setLayoutParams(scrollParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.parseColor("#F9FCFF"));
        content.setPadding(0, 0, 0, dp(120));

        content.addView(postDetailHeader());

        Message openingMessage = openingMessage(post);
        if (openingMessage != null) {
            content.addView(postDetailLead(post, openingMessage));
        }

        content.addView(thickDivider());

        List<Message> replies = replyMessages(post);
        TextView count = new TextView(this);
        count.setText(replies.size() + " comments");
        count.setTextSize(14);
        count.setTextColor(Color.rgb(93, 102, 117));
        count.setPadding(dp(20), dp(18), dp(20), dp(8));
        applyTimes(count, Typeface.BOLD);
        content.addView(count);

        for (int i = 0; i < replies.size(); i++) {
            content.addView(replyCard(post, replies.get(i)));
            if (i < replies.size() - 1) {
                content.addView(dividerLine());
            }
        }

        scrollView.addView(content);
        screen.addView(scrollView);
        screen.addView(replyComposerDock(post));

        root.removeAllViews();
        root.addView(screen);
    }

    private void showEditPostSettings(Post post) {
        if (post.poster == null || !post.poster.equals(currentUser.id())) {
            toast("Only the author can edit post settings");
            return;
        }

        PostEngagement engagement = engagementService.ensureMetadata(post.id);
        Set<PostEngagement.Tag> selectedTags = new LinkedHashSet<>(engagement.tags());
        if (selectedTags.isEmpty()) selectedTags.add(PostEngagement.Tag.ACADEMIC);
        if (selectedTags.size() > 1) {
            PostEngagement.Tag first = selectedTags.iterator().next();
            selectedTags.clear();
            selectedTags.add(first);
        }
        PostEngagement.Visibility[] selectedVisibility =
                new PostEngagement.Visibility[] {engagement.visibility()};

        LinearLayout content = page("Post settings");
        content.setPadding(dp(20), dp(40), dp(20), dp(20));

        Button back = new Button(this);
        back.setText("Back");
        back.setAllCaps(false);
        back.setTextSize(16);
        back.setTextColor(Color.rgb(21, 23, 26));
        back.setBackgroundColor(Color.parseColor("#D5D5D5"));
        back.setOnClickListener(v -> showPostDetail(post));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56));
        backParams.setMargins(0, dp(18), 0, dp(26));
        content.addView(back, backParams);

        content.addView(sectionLabel("TAGS"));
        content.addView(tagPicker(selectedTags));

        content.addView(sectionLabel("VISIBILITY"));
        content.addView(visibilityPicker(selectedVisibility));

        Button save = primaryButton("Save settings");
        save.setOnClickListener(v -> {
            engagementService.updatePostSettings(post.id, selectedTags, selectedVisibility[0]);
            saveInBackground(() -> {
                toast("Post settings updated");
                showPostDetail(post);
            });
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58));
        saveParams.setMargins(0, dp(18), 0, 0);
        content.addView(save, saveParams);
        setPage(content);
    }

    private void confirmDeletePost(Post post) {
        if (post.poster == null || !post.poster.equals(currentUser.id())) {
            toast("Only the author can delete this post");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete post?")
                .setMessage("This removes the post and all replies from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deletePost(post))
                .show();
    }

    private void deletePost(Post post) {
        boolean removed = PostDAO.getInstance().remove(post);
        PostEngagementDAO.getInstance().remove(engagementService.ensureMetadata(post.id));
        if (!removed) {
            toast("Unable to delete post");
            return;
        }
        saveInBackground(() -> {
            toast("Post deleted");
            showPostList();
        });
    }

    private View messageView(Message message, Runnable onRefresh) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setLayoutParams(blockParams());
        card.setBackgroundColor(Color.WHITE);

        User author = UserDAO.getInstance().getByUUID(message.poster());
        String byline = author == null ? "Unknown user" : author.username();
        card.addView(smallText(byline + (message.isHidden() ? " · hidden" : "")));
        card.addView(bodyText(message.message()));

        if (currentUser.role() == User.Role.Admin) {
            MessageReports reports = ReportDAO.getInstance().getReportsFor(message.id());
            if (reports != null && reports.count() > 0) {
                card.addView(adminMessageReportInfo(reports));
            }
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        if (currentUser.role() == User.Role.Admin) {
            Button toggle = compactButton(message.isHidden() ? "Unhide" : "Hide");
            toggle.setOnClickListener(v -> {
                if (ModerationTools.setHidden(message.id(), currentUser.id(), !message.isHidden())) {
                    saveInBackground(onRefresh);
                }
            });
            actions.addView(toggle);
        } else {
            Button report = compactButton(ModerationTools.hasReported(message.id(), currentUser.id()) ? "Retract" : "Report");
            report.setOnClickListener(v -> {
                boolean changed;
                if (ModerationTools.hasReported(message.id(), currentUser.id())) {
                    changed = ModerationTools.removeReport(message.id(), currentUser.id(), System.currentTimeMillis());
                } else {
                    changed = ModerationTools.addReport(message.id(), currentUser.id(), System.currentTimeMillis());
                }
                if (changed) saveInBackground(onRefresh);
            });
            actions.addView(report);
        }
        card.addView(actions);
        return card;
    }

    private View postDetailHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.setBackgroundColor(Color.parseColor("#7096D1"));

        LinearLayout backGroup = new LinearLayout(this);
        backGroup.setOrientation(LinearLayout.HORIZONTAL);
        backGroup.setGravity(Gravity.CENTER_VERTICAL);
        backGroup.setMinimumWidth(dp(104));
        backGroup.setMinimumHeight(dp(48));
        backGroup.setPadding(dp(6), 0, dp(6), 0);
        backGroup.setOnClickListener(v -> showPostList());

        TextView arrow = new TextView(this);
        arrow.setText("<");
        arrow.setTextSize(22);
        arrow.setTextColor(Color.rgb(9, 42, 122));
        arrow.setPadding(0, 0, dp(10), 0);
        applyTimes(arrow, Typeface.BOLD);
        backGroup.addView(arrow);

        TextView back = new TextView(this);
        back.setText("Back");
        back.setTextSize(18);
        back.setTextColor(Color.rgb(9, 42, 122));
        applyTimes(back, Typeface.BOLD);
        backGroup.addView(back);

        header.addView(backGroup);
        return header;
    }

    private View postDetailLead(Post post, Message openingMessage) {
        User authorUser = UserDAO.getInstance().getByUUID(post.poster);
        boolean adminPost = authorUser != null && authorUser.role() == User.Role.Admin;

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(20), dp(18), dp(20), dp(20));
        section.setBackgroundColor(Color.WHITE);

        section.addView(authorMetaRow(authorUser, displayAuthor(post), openingMessage.timestamp(), adminPost));

        section.addView(titleWithCategories(post.topic, engagementService.ensureMetadata(post.id), 24, Color.rgb(9, 42, 122)));

        TextView body = new TextView(this);
        body.setText(openingMessage.message());
        body.setTextSize(17);
        body.setLineSpacing(0f, 1.3f);
        body.setTextColor(Color.rgb(33, 54, 102));
        applyTimes(body, Typeface.NORMAL);
        section.addView(body);

        if (currentUser.role() == User.Role.Admin) {
            MessageReports reports = ReportDAO.getInstance().getReportsFor(openingMessage.id());
            if (reports != null && reports.count() > 0) {
                section.addView(adminMessageReportInfo(reports));
            }
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = blockParams();
        footerParams.setMargins(0, dp(18), 0, 0);
        footer.setLayoutParams(footerParams);

        footer.addView(replyCountChip(replyMessages(post).size(), "replies"));
        View spacer = new View(this);
        footer.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), 1f));
        footer.addView(postEngagementBar(post, () -> showPostDetail(post), true));

        boolean ownPost = post.poster != null && post.poster.equals(currentUser.id());
        View action = ownPost ? null : detailModerationButton(openingMessage, () -> showPostDetail(post));
        if (action != null) footer.addView(action);
        if (ownPost) footer.addView(ownerPostActionsDropdown(post));
        section.addView(footer);
        return section;
    }

    private View replyCard(Post post, Message reply) {
        User authorUser = UserDAO.getInstance().getByUUID(reply.poster());
        boolean adminReply = authorUser != null && authorUser.role() == User.Role.Admin;
        boolean hiddenForViewer = reply.isHidden() && currentUser.role() != User.Role.Admin;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(edgeToEdgeParams());
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        card.setBackground(cardBackground(adminReply));

        card.addView(authorMetaRow(authorUser, displayAuthor(authorUser), reply.timestamp(), adminReply));

        if (hiddenForViewer) {
            LinearLayout hiddenRow = new LinearLayout(this);
            hiddenRow.setOrientation(LinearLayout.HORIZONTAL);
            hiddenRow.setGravity(Gravity.CENTER_VERTICAL);
            hiddenRow.setPadding(0, dp(6), 0, dp(4));

            TextView hiddenBadge = new TextView(this);
            hiddenBadge.setText("Hidden");
            hiddenBadge.setTextSize(11);
            hiddenBadge.setTextColor(Color.rgb(195, 115, 115));
            hiddenBadge.setBackground(hiddenBadgeBackground());
            hiddenBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
            applyTimes(hiddenBadge, Typeface.NORMAL);
            hiddenRow.addView(hiddenBadge);
            card.addView(hiddenRow);

            TextView placeholder = new TextView(this);
            placeholder.setText("This comment has been hidden by a moderator.");
            placeholder.setTextSize(15);
            placeholder.setTextColor(Color.rgb(153, 163, 186));
            placeholder.setPadding(0, dp(4), 0, 0);
            placeholder.setTypeface(Typeface.create("times new roman", Typeface.ITALIC));
            card.addView(placeholder);
            return card;
        }

        TextView body = new TextView(this);
        body.setText(reply.message());
        body.setTextSize(16);
        body.setLineSpacing(0f, 1.25f);
        body.setTextColor(Color.rgb(33, 54, 102));
        body.setPadding(0, dp(8), 0, 0);
        applyTimes(body, Typeface.NORMAL);
        card.addView(body);

        if (currentUser.role() == User.Role.Admin) {
            MessageReports reports = ReportDAO.getInstance().getReportsFor(reply.id());
            if (reports != null && reports.count() > 0) {
                card.addView(adminMessageReportInfo(reports));
            }
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);
        LinearLayout.LayoutParams footerParams = blockParams();
        footerParams.setMargins(0, dp(12), 0, 0);
        footer.setLayoutParams(footerParams);

        View action = detailModerationButton(reply, () -> showPostDetail(post));
        if (action != null) footer.addView(action);
        card.addView(footer);
        return card;
    }

    private View authorMetaRow(User authorUser, String displayName, long timestamp, boolean adminAuthor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(edgeToEdgeParams());

        TextView avatar = avatarView(authorUser, displayName, 36);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        row.addView(avatar, avatarParams);

        LinearLayout textGroup = new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textGroupParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
        textGroupParams.setMargins(dp(12), 0, 0, 0);
        textGroup.setLayoutParams(textGroupParams);

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(displayName);
        name.setTextSize(14);
        name.setTextColor(Color.rgb(18, 35, 76));
        applyTimes(name, Typeface.BOLD);
        topLine.addView(name);

        if (adminAuthor) {
            TextView adminBadge = new TextView(this);
            adminBadge.setText("Admin");
            adminBadge.setTextSize(11);
            adminBadge.setTextColor(Color.WHITE);
            adminBadge.setBackground(adminBadgeBackground());
            adminBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
            applyTimes(adminBadge, Typeface.NORMAL);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.setMargins(dp(6), 0, 0, 0);
            topLine.addView(adminBadge, badgeParams);
        }

        textGroup.addView(topLine);
        row.addView(textGroup);

        TextView time = new TextView(this);
        time.setText(TimeFormatter.relative(timestamp));
        time.setTextSize(11);
        time.setTextColor(Color.rgb(93, 102, 117));
        time.setGravity(Gravity.END);
        applyTimes(time, Typeface.NORMAL);
        row.addView(time);
        return row;
    }

    private View replyCountChip(int count, String suffix) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);

        ImageView bubble = new ImageView(this);
        Drawable bubbleDrawable = getDrawable(android.R.drawable.sym_action_chat);
        if (bubbleDrawable != null) {
            bubbleDrawable = bubbleDrawable.mutate();
            bubbleDrawable.setTint(Color.parseColor("#C7D8F2"));
            bubble.setImageDrawable(bubbleDrawable);
        }
        chip.addView(bubble, new LinearLayout.LayoutParams(dp(14), dp(14)));

        TextView label = new TextView(this);
        label.setText(count + " " + suffix);
        label.setTextSize(13);
        label.setTextColor(Color.rgb(93, 102, 117));
        applyTimes(label, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(dp(4), 0, 0, 0);
        chip.addView(label, labelParams);

        return chip;
    }

    private View detailModerationButton(Message message, Runnable onRefresh) {
        if (currentUser.role() == User.Role.Admin) {
            Button toggle = detailActionButton(message.isHidden() ? "Unhide" : "Hide", Color.parseColor("#081F5C"));
            toggle.setOnClickListener(v -> {
                if (ModerationTools.setHidden(message.id(), currentUser.id(), !message.isHidden())) {
                    saveInBackground(onRefresh);
                }
            });
            return toggle;
        }

        boolean reported = ModerationTools.hasReported(message.id(), currentUser.id());
        Button button = detailActionButton(
                reported ? "Retract" : "Report",
                reported ? Color.parseColor("#081F5C") : Color.parseColor("#334EAC"));
        button.setOnClickListener(v -> {
            if (reported) {
                if (ModerationTools.removeReport(message.id(), currentUser.id(), System.currentTimeMillis())) {
                    saveInBackground(onRefresh);
                }
            } else {
                showReportMenu(button, message, onRefresh);
            }
        });
        return button;
    }

    private void showReportMenu(View anchor, Message message, Runnable onRefresh) {
        Report.Type[] types = new Report.Type[] {
                Report.Type.SPAM,
                Report.Type.HARASSMENT,
                Report.Type.HATE_SPEECH,
                Report.Type.VIOLENCE,
                Report.Type.OTHER
        };
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        List<CheckBox> checks = new ArrayList<>();
        for (Report.Type type : types) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(type.label());
            checkBox.setTextSize(15);
            checkBox.setTextColor(Color.rgb(21, 23, 26));
            applyTimes(checkBox, Typeface.NORMAL);
            form.addView(checkBox);
            checks.add(checkBox);
        }

        EditText reason = input("Required when Other is selected", false);
        form.addView(reason);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Report comment")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<Report.Type> selectedTypes = new ArrayList<>();
            for (int i = 0; i < checks.size(); i++) {
                if (checks.get(i).isChecked()) selectedTypes.add(types[i]);
            }
            if (selectedTypes.isEmpty()) {
                toast("Choose at least one report reason");
                return;
            }
            String explanation = reason.getText().toString().trim();
            if (selectedTypes.contains(Report.Type.OTHER) && explanation.isEmpty()) {
                toast("Please explain the Other reason");
                return;
            }
            boolean changed = ModerationTools.addReport(
                    message.id(),
                    currentUser.id(),
                    System.currentTimeMillis(),
                    selectedTypes,
                    explanation);
            if (changed) {
                dialog.dismiss();
                saveInBackground(onRefresh);
            }
        }));
        dialog.show();
    }

    private Button detailActionButton(String text, int backgroundColor) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setBackground(detailActionBackground(backgroundColor));
        button.setPadding(dp(18), dp(8), dp(18), dp(8));
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        applyTimes(button, Typeface.BOLD);
        return button;
    }

    private View ownerPostActionsDropdown(Post post) {
        TextView toggle = new TextView(this);
        toggle.setText("Options");
        toggle.setTextSize(13);
        toggle.setTextColor(Color.rgb(93, 102, 117));
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(dp(12), 0, 0, 0);
        toggle.setClickable(true);
        toggle.setFocusable(true);
        applyTimes(toggle, Typeface.NORMAL);
        toggle.setOnClickListener(v -> showOwnerPostActionsMenu(toggle, post));
        return toggle;
    }

    private void showOwnerPostActionsMenu(View anchor, Post post) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, dp(8), 0, dp(8));
        menu.setBackground(timeMenuBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            menu.setElevation(dp(8));
        }

        PopupWindow popup = new PopupWindow(
                menu,
                dp(160),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView settings = ownerActionMenuItem("Settings", Color.rgb(47, 50, 58));
        settings.setOnClickListener(v -> {
            popup.dismiss();
            showEditPostSettings(post);
        });
        menu.addView(settings);

        TextView delete = ownerActionMenuItem("Delete", Color.parseColor("#B42318"));
        delete.setOnClickListener(v -> {
            popup.dismiss();
            confirmDeletePost(post);
        });
        menu.addView(delete);

        popup.showAsDropDown(anchor, -dp(128), dp(2));
    }

    private TextView ownerActionMenuItem(String text, int textColor) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextSize(13);
        item.setTextColor(textColor);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16), dp(8), dp(16), dp(8));
        item.setClickable(true);
        item.setFocusable(true);
        applyTimes(item, Typeface.NORMAL);
        return item;
    }

    private View thickDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)));
        divider.setBackgroundColor(Color.parseColor("#E8EEF8"));
        return divider;
    }

    private View replyComposerDock(Post post) {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(20), dp(12), dp(20), dp(18));
        dock.setBackgroundColor(Color.WHITE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        dock.setLayoutParams(params);
        dock.setClickable(true);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E8EEF8"));
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(1));
        dividerParams.gravity = Gravity.TOP;
        divider.setLayoutParams(dividerParams);
        container.addView(divider);

        EditText input = new EditText(this);
        input.setHint("Join the conversation");
        input.setHintTextColor(Color.rgb(152, 170, 205));
        input.setTextSize(16);
        input.setTextColor(Color.rgb(21, 23, 26));
        input.setBackground(replyInputBackground());
        input.setPadding(dp(20), dp(16), dp(20), dp(16));
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        enableClipboard(input);
        applyTimes(input, Typeface.NORMAL);
        dock.addView(input);

        Button send = new Button(this);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        sendParams.setMargins(dp(14), 0, 0, 0);
        send.setLayoutParams(sendParams);
        send.setText(">");
        send.setTextSize(22);
        send.setTextColor(Color.WHITE);
        send.setBackground(sendButtonBackground());
        applyTimes(send, Typeface.BOLD);
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                toast("Please enter a reply");
                return;
            }

            Message reply = new Message(
                    UUID.randomUUID(),
                    currentUser.id(),
                    post.id,
                    System.currentTimeMillis(),
                    text);

            if (!post.messages.insert(reply)) {
                toast("Unable to post reply");
                return;
            }

            saveInBackground(() -> {
                toast("Reply posted");
                showPostDetail(post);
            });
        });
        dock.addView(send);

        container.addView(dock);
        return container;
    }

    private View postListHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.setBackgroundColor(Color.parseColor("#7096D1"));

        Button signOut = textActionButton("Sign out");
        applyTimes(signOut, Typeface.NORMAL);
        signOut.setOnClickListener(v -> {
            currentUser = null;
            showLogin();
        });
        header.addView(signOut);

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);

        TextView headerAvatar = avatarView(currentUser, currentUser.username(), 28);
        LinearLayout.LayoutParams headerAvatarParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        identity.addView(headerAvatar, headerAvatarParams);

        TextView username = new TextView(this);
        username.setText(currentUser.username());
        username.setTextSize(15);
        username.setTextColor(Color.rgb(21, 23, 26));
        applyTimes(username, Typeface.BOLD);
        LinearLayout.LayoutParams headerNameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headerNameParams.setMargins(dp(8), 0, 0, 0);
        identity.addView(username, headerNameParams);

        header.addView(identity);
        return header;
    }

    private View postCard(Post post, boolean isAdmin) {
        return postCard(post, isAdmin, this::showPostList);
    }

    private View postCard(Post post, boolean isAdmin, Runnable onRefresh) {
        Message previewMessage = previewMessage(post, isAdmin);
        long timestamp = postTimestamp(post, isAdmin);
        int replyCount = replyCount(post, isAdmin);
        User authorUser = UserDAO.getInstance().getByUUID(post.poster);
        boolean adminPost = authorUser != null && authorUser.role() == User.Role.Admin;
        boolean reportedPost = postHasReports(post);
        PostEngagement engagement = engagementService.ensureMetadata(post.id);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(blockParams());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardBackground(isAdmin && reportedPost));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showPostDetail(post));

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setLayoutParams(blockParams());

        TextView avatar = avatarView(authorUser, displayAuthor(post), 28);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        avatarParams.setMargins(0, 0, dp(10), 0);
        metaRow.addView(avatar, avatarParams);

        TextView author = new TextView(this);
        author.setText(displayAuthor(post));
        author.setTextSize(11);
        author.setTextColor(Color.rgb(93, 102, 117));
        applyTimes(author, Typeface.NORMAL);
        LinearLayout authorGroup = new LinearLayout(this);
        authorGroup.setOrientation(LinearLayout.HORIZONTAL);
        authorGroup.setGravity(Gravity.CENTER_VERTICAL);
        authorGroup.addView(author);

        if (adminPost) {
            TextView adminBadge = new TextView(this);
            adminBadge.setText("Admin");
            adminBadge.setTextSize(11);
            adminBadge.setTextColor(Color.WHITE);
            adminBadge.setBackground(adminBadgeBackground());
            adminBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
            applyTimes(adminBadge, Typeface.NORMAL);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.setMargins(dp(6), 0, 0, 0);
            authorGroup.addView(adminBadge, badgeParams);
        }

        metaRow.addView(authorGroup, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView time = new TextView(this);
        time.setText(timestamp == Long.MIN_VALUE ? "just now" : TimeFormatter.relative(timestamp));
        time.setTextSize(11);
        time.setTextColor(Color.rgb(93, 102, 117));
        time.setGravity(Gravity.END);
        applyTimes(time, Typeface.NORMAL);
        metaRow.addView(time);
        card.addView(metaRow);

        card.addView(titleWithCategories(post.topic, engagement, 18, Color.rgb(21, 23, 26)));

        TextView preview = new TextView(this);
        preview.setText(previewMessage == null ? "" : previewMessage.message());
        preview.setTextSize(15);
        preview.setTextColor(Color.rgb(51, 62, 80));
        preview.setLineSpacing(0f, 1.25f);
        preview.setMaxLines(2);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setPadding(0, 0, 0, dp(12));
        applyTimes(preview, Typeface.NORMAL);
        card.addView(preview);

        LinearLayout repliesRow = new LinearLayout(this);
        repliesRow.setOrientation(LinearLayout.HORIZONTAL);
        repliesRow.setGravity(Gravity.CENTER_VERTICAL);
        repliesRow.setLayoutParams(blockParams());

        View commentView = replyCountChip(replyCount, "comments");
        repliesRow.addView(commentView);

        View rowSpacer = new View(this);
        repliesRow.addView(rowSpacer, new LinearLayout.LayoutParams(0, dp(1), 1f));

        View engagementView = postEngagementBar(post, onRefresh, true);
        repliesRow.addView(engagementView);

        card.addView(repliesRow);

        if (isAdmin && reportedPost) {
            card.addView(adminPostReportSummary(post));
        }

        return card;
    }

    private View trendingPostCard(Post post, boolean isAdmin) {
        PostEngagement.Tag tag = engagementService.ensureMetadata(post.id).tag();
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(blockParams());
        wrapper.addView(postCard(post, isAdmin, () -> showTrendingPage(tag)));

        TextView score = smallText(String.format(Locale.getDefault(), "Hot score %.1f", engagementService.score(post, isAdmin)));
        score.setGravity(Gravity.END);
        score.setPadding(0, 0, dp(4), dp(8));
        applyTimes(score, Typeface.BOLD);
        wrapper.addView(score);
        return wrapper;
    }

    private View landingTabs(boolean trending, PostEngagement.Tag selectedTag) {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(20), dp(14), dp(20), dp(8));
        tabs.setBackgroundColor(Color.WHITE);

        Button posts = tabButton("Posts", !trending);
        posts.setOnClickListener(v -> showPostList());
        tabs.addView(posts, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button hot = tabButton("Trending", trending);
        hot.setOnClickListener(v -> showTrendingPage(selectedTag == null ? PostEngagement.Tag.ACADEMIC : selectedTag));
        LinearLayout.LayoutParams hotParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        hotParams.setMargins(dp(10), 0, 0, 0);
        tabs.addView(hot, hotParams);
        return tabs;
    }

    private Button tabButton(String text, boolean selected) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(selected ? Color.WHITE : Color.rgb(9, 42, 122));
        button.setBackground(tabBackground(selected));
        applyTimes(button, Typeface.BOLD);
        return button;
    }

    private GradientDrawable timeMenuBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private GradientDrawable tagDescriptionBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#EEF1F5"));
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), Color.parseColor("#DDE3EC"));
        return drawable;
    }

    private View tagPicker(Set<PostEngagement.Tag> selectedTags) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(10), 0, dp(22));

        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);

        TextView description = new TextView(this);
        description.setTextSize(13);
        description.setTextColor(Color.rgb(59, 70, 88));
        description.setGravity(Gravity.CENTER_VERTICAL);
        description.setBackground(tagDescriptionBackground());
        description.setPadding(dp(12), dp(8), dp(12), dp(8));
        description.setText(allTagDescriptions());
        applyTimes(description, Typeface.NORMAL);

        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.setMargins(0, dp(10), 0, 0);
        description.setLayoutParams(descriptionParams);

        List<Button> buttons = new ArrayList<>();
        for (PostEngagement.Tag tag : PostEngagement.Tag.values()) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText("#" + tag.label());
            button.setTextSize(13);
            button.setMinHeight(dp(44));
            button.setMinimumHeight(dp(44));
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setContentDescription(tag.label() + ". " + tagDescription(tag));
            applyTimes(button, Typeface.BOLD);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (tag != PostEngagement.Tag.LOUNGE) params.setMargins(0, 0, dp(8), 0);
            tags.addView(button, params);
            buttons.add(button);

            button.setOnClickListener(v -> {
                selectedTags.clear();
                selectedTags.add(tag);
                for (int i = 0; i < buttons.size(); i++) {
                    PostEngagement.Tag itemTag = PostEngagement.Tag.values()[i];
                    styleTagButton(buttons.get(i), selectedTags.contains(itemTag));
                }
            });
        }

        for (int i = 0; i < buttons.size(); i++) {
            styleTagButton(buttons.get(i), selectedTags.contains(PostEngagement.Tag.values()[i]));
        }

        container.addView(tags);
        container.addView(description);
        return container;
    }

    private String tagDescription(PostEngagement.Tag tag) {
        switch (tag) {
            case ACADEMIC:
                return "Coursework, exams, resources, Q&A";
            case CAMPUS:
                return "Events, clubs, dining, deals, lost & found";
            case LOUNGE:
                return "Hobbies, rants, fun & random";
            default:
                return "";
        }
    }

    private String allTagDescriptions() {
        StringBuilder text = new StringBuilder();
        for (PostEngagement.Tag tag : PostEngagement.Tag.values()) {
            if (text.length() > 0) text.append("\n");
            text.append(tag.label()).append(": ").append(tagDescription(tag));
        }
        return text.toString();
    }

    private View collapsibleVisibilityPicker(PostEngagement.Visibility[] selectedVisibility) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, 0, 0, dp(6));

        Button toggle = new Button(this);
        toggle.setAllCaps(false);
        toggle.setTextSize(12);
        toggle.setMinHeight(dp(34));
        toggle.setMinimumHeight(dp(34));
        toggle.setPadding(dp(12), 0, dp(12), 0);
        applyTimes(toggle, Typeface.NORMAL);
        styleTagButton(toggle, false);
        section.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)));

        final boolean[] expanded = new boolean[] {false};
        final Runnable[] updateLabel = new Runnable[1];
        final View[] optionsHolder = new View[1];
        updateLabel[0] = () -> {
            toggle.setText("Visibility: " + selectedVisibility[0].label() + (expanded[0] ? "  ▲" : "  ▼"));
            if (optionsHolder[0] != null) optionsHolder[0].setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
        };

        View options = visibilityPicker(selectedVisibility, () -> {
            expanded[0] = false;
            updateLabel[0].run();
        });
        optionsHolder[0] = options;
        options.setVisibility(View.GONE);
        section.addView(options);
        updateLabel[0].run();

        toggle.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            updateLabel[0].run();
        });

        return section;
    }
    private View visibilityPicker(PostEngagement.Visibility[] selectedVisibility) {
        return visibilityPicker(selectedVisibility, null);
    }

    private View visibilityPicker(PostEngagement.Visibility[] selectedVisibility, Runnable onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(22));
        List<Button> buttons = new ArrayList<>();

        for (PostEngagement.Visibility visibility : PostEngagement.Visibility.values()) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(visibility.label());
            button.setTextSize(12);
            button.setMinHeight(dp(38));
            button.setMinimumHeight(dp(38));
            button.setPadding(dp(10), 0, dp(10), 0);
            applyTimes(button, Typeface.BOLD);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
            params.setMargins(0, 0, dp(8), 0);
            row.addView(button, params);
            buttons.add(button);
            button.setOnClickListener(v -> {
                selectedVisibility[0] = visibility;
                for (int i = 0; i < buttons.size(); i++) {
                    styleTagButton(buttons.get(i), PostEngagement.Visibility.values()[i] == selectedVisibility[0]);
                }
                if (onChange != null) onChange.run();
            });
        }

        for (int i = 0; i < buttons.size(); i++) {
            styleTagButton(buttons.get(i), PostEngagement.Visibility.values()[i] == selectedVisibility[0]);
        }
        return row;
    }

    private void styleTagButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : Color.rgb(59, 86, 162));
        button.setBackground(tagBackground(selected));
    }

    private View tagJumpBar(PostEngagement.Tag activeTag) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(14), 0, dp(22));

        TextView label = sectionLabel("CATEGORIES");
        section.addView(label);

        FlowLayout chips = new FlowLayout(this, dp(8), dp(8));
        chips.setPadding(0, dp(10), 0, 0);

        for (PostEngagement.Tag tag : PostEngagement.Tag.values()) {
            TextView chip = tagChip(tag, tag == activeTag);
            chip.setOnClickListener(v -> showTrendingPage(tag));
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chips.addView(chip, params);
        }
        section.addView(chips);
        return section;
    }

    private TextView tagChip(PostEngagement.Tag tag, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText("#" + tag.label());
        chip.setTextSize(12);
        chip.setTextColor(selected ? Color.WHITE : Color.rgb(59, 86, 162));
        chip.setBackground(tagBackground(selected));
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> showTrendingPage(tag));
        applyTimes(chip, Typeface.BOLD);
        return chip;
    }

    private View titleWithCategories(String topic, PostEngagement engagement, int titleSize, int titleColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(8));

        TextView title = new TextView(this);
        title.setText(topic);
        title.setTextSize(titleSize);
        title.setTextColor(titleColor);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        applyTimes(title, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

        for (PostEngagement.Tag tag : engagement.tags()) {
            TextView chip = displayCategoryChip("#" + tag.label());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(dp(6), 0, 0, 0);
            chips.addView(chip, params);
        }

        scroll.addView(chips);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.setMargins(dp(10), 0, 0, 0);
        row.addView(scroll, scrollParams);
        return row;
    }

    private TextView displayCategoryChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(12);
        chip.setTextColor(Color.rgb(59, 86, 162));
        chip.setBackground(displayCategoryBackground());
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        applyTimes(chip, Typeface.BOLD);
        return chip;
    }

    private View postTagsRow(PostEngagement engagement, boolean selected) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(10));

        for (PostEngagement.Tag tag : engagement.tags()) {
            TextView chip = tagChip(tag, selected);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), 0);
            row.addView(chip, params);
        }

        TextView visibility = new TextView(this);
        visibility.setText(engagement.visibility().label());
        visibility.setTextSize(12);
        visibility.setTextColor(Color.rgb(93, 102, 117));
        visibility.setPadding(dp(10), dp(5), dp(10), dp(5));
        visibility.setBackground(replyChipBackground());
        applyTimes(visibility, Typeface.BOLD);
        row.addView(visibility);

        scroll.addView(row);
        return scroll;
    }

    private View postEngagementBar(Post post, Runnable onRefresh, boolean compact) {
        PostEngagement engagement = engagementService.ensureMetadata(post.id);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(compact ? dp(8) : 0, 0, 0, 0);

        row.addView(engagementChip(
                "\u25B2",
                engagement.likes(),
                engagement.likedBy(currentUser.id()),
                () -> engagementService.toggleLike(post.id, currentUser.id()),
                onRefresh));
        row.addView(engagementChip(
                "\u25BC",
                engagement.dislikes(),
                engagement.dislikedBy(currentUser.id()),
                () -> engagementService.toggleDislike(post.id, currentUser.id()),
                onRefresh));
        return row;
    }

    private View engagementChip(String iconText, int count, boolean selected, Runnable action, Runnable onRefresh) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setClickable(true);
        chip.setFocusable(true);

        int activeColor = Color.parseColor("#081F5C");
        int inactiveColor = Color.rgb(136, 148, 166);
        int textColor = selected ? activeColor : inactiveColor;

        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextSize(13);
        icon.setTextColor(textColor);
        applyTimes(icon, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.addView(icon);

        TextView label = new TextView(this);
        label.setText(String.valueOf(count));
        label.setTextSize(13);
        label.setTextColor(textColor);
        applyTimes(label, selected ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(dp(4), 0, 0, 0);
        chip.addView(label, labelParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(18), 0);
        chip.setLayoutParams(params);

        chip.setOnClickListener(v -> {
            action.run();
            saveInBackground(onRefresh);
        });
        return chip;
    }
    private View postComposerDock(boolean trending, PostEngagement.Tag selectedTag) {
        FrameLayout dock = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(124));
        params.gravity = Gravity.BOTTOM;
        dock.setLayoutParams(params);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), 0, dp(20), 0);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        barParams.gravity = Gravity.BOTTOM;
        bar.setLayoutParams(barParams);
        bar.setBackground(dockBackground());
        bar.setVisibility(View.VISIBLE);
        bar.setAlpha(1f);
        bottomNavBar = bar;

        View posts = bottomNavItem(true, "Posts", !trending);
        posts.setOnClickListener(v -> showPostList());
        bar.addView(posts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        View hot = bottomNavItem(false, "Trending", trending);
        hot.setOnClickListener(v -> showTrendingPage(selectedTag == null ? PostEngagement.Tag.ACADEMIC : selectedTag));
        bar.addView(hot, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        dock.addView(bar);
        dock.setOnTouchListener((v, event) -> {
            revealBottomNav(bar);
            return false;
        });

        if (!trending) {
            Button createPost = new Button(this);
            FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(dp(56), dp(56));
            buttonParams.gravity = Gravity.TOP | Gravity.END;
            buttonParams.topMargin = dp(10);
            buttonParams.rightMargin = dp(20);
            createPost.setLayoutParams(buttonParams);
            createPost.setText("+");
            createPost.setTextSize(24);
            applyTimes(createPost, Typeface.BOLD);
            createPost.setAllCaps(false);
            createPost.setTextColor(Color.WHITE);
            createPost.setBackground(fabBackground());
            createPost.setOnClickListener(v -> showCreatePost());
            dock.addView(createPost);
        }

        return dock;
    }

    private void revealBottomNav(View bar) {
        if (bar.getVisibility() == View.VISIBLE && bar.getAlpha() == 1f) return;
        bar.setVisibility(View.VISIBLE);
        bar.animate().alpha(1f).setDuration(120).start();
    }

    private void hideBottomNav(View bar) {
        if (bar.getVisibility() != View.VISIBLE) return;
        bar.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> bar.setVisibility(View.INVISIBLE))
                .start();
    }

    private View bottomNavItem(boolean commentIcon, String text, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);

        int activeColor = Color.parseColor("#081F5C");
        int inactiveColor = Color.rgb(93, 102, 117);
        int itemColor = selected ? activeColor : inactiveColor;

        if (commentIcon) {
            ImageView icon = new ImageView(this);
            Drawable drawable = getDrawable(android.R.drawable.sym_action_chat);
            if (drawable != null) {
                drawable = drawable.mutate();
                drawable.setTint(itemColor);
                icon.setImageDrawable(drawable);
            }
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
            iconParams.setMargins(0, 0, dp(6), 0);
            item.addView(icon, iconParams);
        } else {
            TextView rocketIcon = new TextView(this);
            rocketIcon.setText("🚀");
            rocketIcon.setTextSize(15);
            rocketIcon.setTextColor(itemColor);
            rocketIcon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            iconParams.setMargins(0, 0, dp(6), 0);
            item.addView(rocketIcon, iconParams);
        }

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTextColor(itemColor);
        label.setGravity(Gravity.CENTER);
        applyTimes(label, Typeface.BOLD);
        item.addView(label);
        return item;
    }

    private void showTrendingPage(PostEngagement.Tag selectedTag) {
        boolean isAdmin = currentUser.role() == User.Role.Admin;
        PostEngagement.Tag activeTag = selectedTag == null ? PostEngagement.Tag.ACADEMIC : selectedTag;

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.parseColor("#F9FCFF"));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(124));
        content.setBackgroundColor(Color.parseColor("#F9FCFF"));

        content.addView(postListHeader());

        LinearLayout listSection = new LinearLayout(this);
        listSection.setOrientation(LinearLayout.VERTICAL);
        listSection.setPadding(dp(20), dp(18), dp(20), 0);

        TextView title = new TextView(this);
        title.setText("Trending in #" + activeTag.label());
        title.setTextSize(22);
        title.setTextColor(Color.rgb(9, 42, 122));
        title.setPadding(0, 0, 0, dp(8));
        applyTimes(title, Typeface.BOLD);
        listSection.addView(title);

        List<Post> posts = new ArrayList<>();
        for (Post item : engagementService.trendingPosts(activeTag, isAdmin)) {
            if (canViewPost(item)) posts.add(item);
        }
        if (posts.isEmpty()) {
            TextView empty = bodyText("No posts in this tag yet.");
            empty.setPadding(0, dp(28), 0, dp(28));
            listSection.addView(empty);
        } else {
            for (Post post : posts) {
                listSection.addView(trendingPostCard(post, isAdmin));
            }
        }

        listSection.addView(tagJumpBar(activeTag));
        content.addView(listSection);

        scrollView.addView(content);
        screen.addView(scrollView);
        screen.addView(postComposerDock(true, activeTag));

        root.removeAllViews();
        root.addView(screen);
    }

    private void showAdminPanel() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.parseColor("#F9FCFF"));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(24));
        content.setBackgroundColor(Color.parseColor("#F9FCFF"));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.setBackgroundColor(Color.parseColor("#7096D1"));

        LinearLayout backGroup = new LinearLayout(this);
        backGroup.setOrientation(LinearLayout.HORIZONTAL);
        backGroup.setGravity(Gravity.CENTER_VERTICAL);
        backGroup.setMinimumWidth(dp(104));
        backGroup.setMinimumHeight(dp(48));
        backGroup.setPadding(dp(6), 0, dp(6), 0);
        backGroup.setOnClickListener(v -> showPostList());

        TextView arrow = new TextView(this);
        arrow.setText("<");
        arrow.setTextSize(22);
        arrow.setTextColor(Color.rgb(9, 42, 122));
        arrow.setPadding(0, 0, dp(10), 0);
        applyTimes(arrow, Typeface.BOLD);
        backGroup.addView(arrow);

        TextView back = new TextView(this);
        back.setText("Back");
        back.setTextSize(18);
        back.setTextColor(Color.rgb(9, 42, 122));
        applyTimes(back, Typeface.BOLD);
        backGroup.addView(back);

        header.addView(backGroup);

        TextView title = new TextView(this);
        title.setText("Admin Dashboard");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#092A7A"));
        title.setGravity(Gravity.CENTER);
        applyTimes(title, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView adminBadge = new TextView(this);
        adminBadge.setText("Admin");
        adminBadge.setTextSize(11);
        adminBadge.setTextColor(Color.WHITE);
        adminBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
        adminBadge.setBackground(adminBadgeBackground());
        applyTimes(adminBadge, Typeface.BOLD);
        header.addView(adminBadge);

        content.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(24));

        List<Message> reportedMessages = reportedMessagesForAdmin(adminSortMode, 100);

        int hiddenReported = 0;
        int urgentCases = 0;
        for (Message message : reportedMessages) {
            MessageReports reports = ReportDAO.getInstance().getReportsFor(message.id());
            if (reports != null && isUrgentReport(reports)) urgentCases++;
            if (message.isHidden()) hiddenReported++;
        }

        body.addView(adminSummaryCard(reportedMessages.size(), urgentCases, hiddenReported));
        body.addView(adminSortRow());

        TextView current = new TextView(this);
        current.setText("Current strategy: " + adminSortMode);
        current.setTextSize(13);
        current.setTextColor(Color.rgb(93, 102, 117));
        current.setPadding(0, dp(8), 0, dp(8));
        applyTimes(current, Typeface.BOLD);
        body.addView(current);

        if (reportedMessages.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(16), dp(16), dp(16), dp(16));
            empty.setBackground(reportCardBackground(Color.WHITE));

            TextView emptyText = new TextView(this);
            emptyText.setText("No reported messages yet.");
            emptyText.setTextSize(16);
            emptyText.setTextColor(Color.parseColor("#213666"));
            applyTimes(emptyText, Typeface.NORMAL);
            empty.addView(emptyText);
            body.addView(empty);
        } else {
            for (Message message : reportedMessages) {
                MessageReports reports = ReportDAO.getInstance().getReportsFor(message.id());
                body.addView(adminReportCard(message, reports));
            }
        }

        content.addView(body);
        scrollView.addView(content);
        screen.addView(scrollView);

        root.removeAllViews();
        root.addView(screen);
    }

    private View adminSummaryCard(int reportedMessages, int urgentCases, int hiddenReported) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(reportCardBackground(Color.WHITE));

        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText("Report summary");
        title.setTextSize(18);
        title.setTextColor(Color.parseColor("#081F5C"));
        applyTimes(title, Typeface.BOLD);
        card.addView(title);

        card.addView(summaryLine("Reported messages", reportedMessages));
        card.addView(summaryLine("Urgent cases", urgentCases));
        card.addView(summaryLine("Hidden reported", hiddenReported));
        return card;
    }

    private View summaryLine(String label, int value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(15);
        labelView.setTextColor(Color.parseColor("#213666"));
        applyTimes(labelView, Typeface.NORMAL);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(String.valueOf(value));
        valueView.setTextSize(18);
        valueView.setTextColor(Color.parseColor("#081F5C"));
        applyTimes(valueView, Typeface.BOLD);
        row.addView(valueView);
        return row;
    }

    private View adminSortRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(adminSortButton("Priority", "PRIORITY"), sortButtonParams());
        row.addView(adminSortButton("Most", "MOST"), sortButtonParams());
        row.addView(adminSortButton("Oldest", "OLDEST"), sortButtonParams());
        return row;
    }

    private LinearLayout.LayoutParams sortButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private Button adminSortButton(String label, String mode) {
        boolean active = adminSortMode.equals(mode);
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(active ? "✓ " + label : label);
        button.setTextSize(14);
        button.setTextColor(active ? Color.WHITE : Color.parseColor("#081F5C"));
        button.setBackground(detailActionBackground(active ? Color.parseColor("#081F5C") : Color.parseColor("#C7D8F2")));
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        applyTimes(button, Typeface.BOLD);
        button.setOnClickListener(v -> {
            adminSortMode = mode;
            showAdminPanel();
        });
        return button;
    }

    private View adminReportCard(Message message, MessageReports reports) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(reportCardBackground(Color.WHITE));

        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, dp(8), 0, dp(12));
        card.setLayoutParams(params);

        int reportCount = reports == null ? 0 : reports.count();
        Report.Type topType = reports == null ? Report.Type.OTHER : topReportType(reports);
        String priority = reports == null ? "MEDIUM" : priorityLabel(reports);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(labelBadge(priority, reportChipBackground(priorityColor(priority)), Color.WHITE));

        TextView typeBadge = labelBadge(topType.label(), reportChipBackground(Color.parseColor("#E7F1FF")), Color.rgb(59, 86, 162));
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        typeParams.setMargins(dp(8), 0, 0, 0);
        top.addView(typeBadge, typeParams);

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));

        TextView count = new TextView(this);
        count.setText(reportCount + " report" + (reportCount == 1 ? "" : "s"));
        count.setTextSize(13);
        count.setTextColor(Color.parseColor("#213666"));
        applyTimes(count, Typeface.BOLD);
        top.addView(count);
        card.addView(top);

        User author = UserDAO.getInstance().getByUUID(message.poster());
        String authorName = author == null ? "Unknown user" : author.username();

        TextView meta = new TextView(this);
        meta.setText("Status: " + (message.isHidden() ? "Hidden" : "Visible") + " · Posted by: " + authorName);
        meta.setTextSize(13);
        meta.setTextColor(Color.rgb(93, 102, 117));
        meta.setPadding(0, dp(10), 0, dp(8));
        applyTimes(meta, Typeface.NORMAL);
        card.addView(meta);

        TextView content = new TextView(this);
        content.setText(message.message());
        content.setTextSize(16);
        content.setTextColor(Color.parseColor("#213666"));
        content.setLineSpacing(0f, 1.25f);
        content.setPadding(0, 0, 0, dp(14));
        applyTimes(content, Typeface.NORMAL);
        card.addView(content);

        Button toggle = detailActionButton(message.isHidden() ? "Unhide message" : "Hide message", Color.parseColor("#081F5C"));
        toggle.setOnClickListener(v -> {
            boolean newState = !message.isHidden();
            if (ModerationTools.setHidden(message.id(), currentUser.id(), newState)) {
                toast(newState ? "Message hidden" : "Message unhidden");
                saveInBackground(this::showAdminPanel);
            }
        });
        card.addView(toggle);
        return card;
    }

    private void seedDemoDataIfEmpty() {
        if (UserDAO.getInstance().getAll().hasNext()) return;

        User admin = new User(UUID.randomUUID(), User.Role.Admin, "admin", "admin123");
        UserDAO.getInstance().add(admin);

        User member = UserDAO.getInstance().register("member", "member123");
        User student2 = UserDAO.getInstance().register("student2", "student123");
        User student3 = UserDAO.getInstance().register("student3", "student123");
        User student4 = UserDAO.getInstance().register("student4", "student123");
        User student5 = UserDAO.getInstance().register("student5", "student123");

        if (member == null || student2 == null || student3 == null || student4 == null || student5 == null) {
            dataManager.writeAll();
            return;
        }

        Post safetyPost = createSeedPost(member, "Unsafe comment about ANU campus event", "Someone posted threatening language about a student group after the ANU society event near Kambri. This needs moderator review.", PostEngagement.Tag.CAMPUS);
        Message violenceMessage = addSeedReply(safetyPost, student2, "This comment includes a threat about meeting someone outside Chifley Library after class.");

        Post coursePost = createSeedPost(student2, "COMP2100 tutorial discussion", "Can someone explain how the moderation system is supposed to work for the assignment?", PostEngagement.Tag.ACADEMIC);
        Message harassmentMessage = addSeedReply(coursePost, student3, "A reply in this thread keeps targeting one student by name and insulting their work in the tutorial group.");

        Post spamPost = createSeedPost(student3, "ANU textbook exchange", "Does anyone know where to find second-hand textbooks around campus?", PostEngagement.Tag.LOUNGE);
        Message spamMessage = addSeedReply(spamPost, student4, "Repeated advertisement: cheap assignment help and guaranteed HD results. Message me now.");

        Post policyPost = createSeedPost(student4, "Question about late submission policy", "I am confused about the course late submission rules and special consideration process.", PostEngagement.Tag.ACADEMIC);
        Message otherMessage = addSeedReply(policyPost, member, "This reply gives misleading academic policy information and may confuse first-year students.");

        Post hatePost = createSeedPost(student5, "Discussion about group project teams", "How should we handle conflict in COMP group assignments?", PostEngagement.Tag.ACADEMIC);
        Message hateMessage = addSeedReply(hatePost, member, "A comment here attacks a group of students with hateful language and should be checked by an admin.");

        Post quietStudyPost = createSeedPost(student2, "Best quiet study spots at ANU", "I usually study around Marie Reay or the Law Library, but I am looking for somewhere quiet during exam week. Any recommendations?", PostEngagement.Tag.ACADEMIC);
        addSeedReply(quietStudyPost, member, "The Law Library is usually quieter than Hancock in the afternoon.");

        Post projectHelpPost = createSeedPost(student3, "COMP2100 project structure question", "Our group is deciding how to separate UI, persistence and moderation logic. Is it better to keep admin UI separate from user-facing screens?", PostEngagement.Tag.ACADEMIC);
        addSeedReply(projectHelpPost, student5, "We kept the admin dashboard separate, but still linked it from the main post list for admins.");

        Post foodPost = createSeedPost(member, "Food options near Kambri after 6pm", "Does anyone know which places around Kambri are still open after evening tutorials?", PostEngagement.Tag.LOUNGE);
        addSeedReply(foodPost, student4, "Supermarket and a few takeaway places are usually open later than the cafes.");

        long now = System.currentTimeMillis();
        ModerationTools.addReport(violenceMessage.id(), member.id(), now - 5 * 60 * 1000L, Report.Type.VIOLENCE);
        ModerationTools.addReport(violenceMessage.id(), student3.id(), now - 4 * 60 * 1000L, Report.Type.VIOLENCE);
        ModerationTools.addReport(violenceMessage.id(), student4.id(), now - 3 * 60 * 1000L, Report.Type.VIOLENCE);
        ModerationTools.addReport(harassmentMessage.id(), member.id(), now - 30 * 60 * 1000L, Report.Type.HARASSMENT);
        ModerationTools.addReport(harassmentMessage.id(), student2.id(), now - 29 * 60 * 1000L, Report.Type.HARASSMENT);
        ModerationTools.addReport(harassmentMessage.id(), student4.id(), now - 28 * 60 * 1000L, Report.Type.HARASSMENT);
        ModerationTools.addReport(harassmentMessage.id(), student5.id(), now - 27 * 60 * 1000L, Report.Type.HARASSMENT);
        ModerationTools.addReport(harassmentMessage.id(), admin.id(), now - 26 * 60 * 1000L, Report.Type.HARASSMENT);
        ModerationTools.addReport(spamMessage.id(), member.id(), now - 15 * 60 * 1000L, Report.Type.SPAM);
        ModerationTools.addReport(spamMessage.id(), student2.id(), now - 14 * 60 * 1000L, Report.Type.SPAM);
        ModerationTools.addReport(otherMessage.id(), student2.id(), now - 120 * 60 * 1000L, Report.Type.OTHER);
        ModerationTools.addReport(hateMessage.id(), student3.id(), now - 45 * 60 * 1000L, Report.Type.HATE_SPEECH);
        ModerationTools.addReport(hateMessage.id(), student4.id(), now - 44 * 60 * 1000L, Report.Type.HATE_SPEECH);
        ModerationTools.addReport(hateMessage.id(), student5.id(), now - 43 * 60 * 1000L, Report.Type.HATE_SPEECH);
        ModerationTools.addReport(hateMessage.id(), member.id(), now - 42 * 60 * 1000L, Report.Type.HATE_SPEECH);

        dataManager.writeAll();
    }

    private Post createSeedPost(User author, String topic, String body, PostEngagement.Tag tag) {
        Post post = new Post(UUID.randomUUID(), author.id(), topic);
        Message opening = new Message(UUID.randomUUID(), author.id(), post.id, System.currentTimeMillis(), body);
        PostDAO.getInstance().add(post);
        post.messages.insert(opening);
        PostEngagement engagement = engagementService.ensureMetadata(post.id);
        engagement.setVisibility(PostEngagement.Visibility.PUBLIC);
        engagement.setTag(tag == null ? PostEngagement.Tag.ACADEMIC : tag);
        return post;
    }

    private Message addSeedReply(Post post, User author, String body) {
        Message reply = new Message(UUID.randomUUID(), author.id(), post.id, System.currentTimeMillis(), body);
        post.messages.insert(reply);
        return reply;
    }

    private LinearLayout page(String title) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(28), dp(20), dp(20));
        layout.setBackgroundColor(Color.parseColor("#F9FCFF"));
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(28);
        heading.setTextColor(Color.rgb(21, 23, 26));
        heading.setPadding(0, 0, 0, dp(16));
        layout.addView(heading);
        return layout;
    }

    private EditText input(String hint, boolean password) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        editText.setLayoutParams(blockParams());
        enableClipboard(editText);
        return editText;
    }

    private Button primaryButton(String text) {
        Button button = compactButton(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(36, 87, 214));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = compactButton(text);
        button.setTextColor(Color.rgb(21, 23, 26));
        return button;
    }

    private Button textActionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setBackground(signOutBackground());
        button.setMinWidth(dp(104));
        button.setMinimumWidth(dp(104));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        return button;
    }

    private void setActionButtonEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextColor(enabled ? Color.WHITE : Color.rgb(134, 158, 198));
        button.setBackground(postActionBackground(enabled));
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setLayoutParams(blockParams());
        return button;
    }

    private TextView smallText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(Color.rgb(93, 102, 117));
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(Color.rgb(21, 23, 26));
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private View gap(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)));
        return view;
    }

    private LinearLayout.LayoutParams edgeToEdgeParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private long postTimestamp(Post post, boolean isAdmin) {
        Message message = previewMessage(post, isAdmin);
        return message == null ? Long.MIN_VALUE : message.timestamp();
    }

    private Message previewMessage(Post post, boolean isAdmin) {
        Iterator<Message> it = post.getVisibleMessages(isAdmin).getAll();
        return it.hasNext() ? it.next() : null;
    }

    private int replyCount(Post post, boolean isAdmin) {
        int count = 0;
        for (Iterator<Message> it = post.getVisibleMessages(isAdmin).getAll(); it.hasNext(); ) {
            it.next();
            count++;
        }
        return Math.max(0, count - 1);
    }

    private boolean canViewPost(Post post) {
        if (post == null || currentUser == null) return false;
        if (post.poster != null && post.poster.equals(currentUser.id())) return true;
        PostEngagement.Visibility visibility = engagementService.ensureMetadata(post.id).visibility();
        if (visibility == PostEngagement.Visibility.PUBLIC) return true;
        return visibility == PostEngagement.Visibility.ADMIN_ONLY
                && currentUser.role() == User.Role.Admin;
    }

    private String displayAuthor(Post post) {
        User author = UserDAO.getInstance().getByUUID(post.poster);
        return author == null ? "Unknown user" : author.username();
    }

    private String displayAuthor(User author) {
        return author == null ? "Unknown user" : author.username();
    }

    private Message openingMessage(Post post) {
        Iterator<Message> it = post.messages.getAll();
        return it.hasNext() ? it.next() : null;
    }

    private List<Message> replyMessages(Post post) {
        ArrayList<Message> replies = new ArrayList<>();
        Iterator<Message> it = post.messages.getAll();
        if (it.hasNext()) it.next();
        while (it.hasNext()) {
            replies.add(it.next());
        }
        return replies;
    }

    private boolean postHasReports(Post post) {
        for (Iterator<Message> it = post.getVisibleMessages(true).getAll(); it.hasNext(); ) {
            Message message = it.next();
            MessageReports reports = ReportDAO.getInstance().getReportsFor(message.id());
            if (reports != null && reports.count() > 0) return true;
        }
        return false;
    }

    private View adminPostReportSummary(Post post) {
        int reportedMessages = 0;
        int totalReports = 0;
        String highestPriority = "LOW";
        for (Iterator<Message> it = post.getVisibleMessages(true).getAll(); it.hasNext(); ) {
            Message message = it.next();
            MessageReports reports = ReportDAO.getInstance().getReportsFor(message.id());
            if (reports != null && reports.count() > 0) {
                reportedMessages++;
                totalReports += reports.count();
                String priority = priorityLabel(reports);
                if (priorityRank(priority) > priorityRank(highestPriority)) highestPriority = priority;
            }
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(reportCardBackground(Color.WHITE));
        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, dp(10), 0, 0);
        box.setLayoutParams(params);
        TextView title = new TextView(this);
        title.setText(highestPriority + " moderation flag");
        title.setTextSize(13);
        title.setTextColor(priorityColor(highestPriority));
        applyTimes(title, Typeface.BOLD);
        box.addView(title);
        TextView detail = new TextView(this);
        detail.setText(reportedMessages + " reported message" + (reportedMessages == 1 ? "" : "s") + " · " + totalReports + " total report" + (totalReports == 1 ? "" : "s"));
        detail.setTextSize(12);
        detail.setTextColor(Color.parseColor("#425A88"));
        detail.setPadding(0, dp(3), 0, 0);
        applyTimes(detail, Typeface.NORMAL);
        box.addView(detail);
        return box;
    }

    private List<Message> reportedMessagesForAdmin(String mode, int amount) {
        ArrayList<Message> messages = new ArrayList<>();
        for (Iterator<Message> it = ModerationTools.getReportedMessages("MOST", Math.max(amount, 200)); it.hasNext(); ) messages.add(it.next());
        messages.sort((left, right) -> {
            MessageReports leftReports = ReportDAO.getInstance().getReportsFor(left.id());
            MessageReports rightReports = ReportDAO.getInstance().getReportsFor(right.id());
            if ("OLDEST".equals(mode)) return Long.compare(left.timestamp(), right.timestamp());
            if ("MOST".equals(mode)) {
                int byCount = Integer.compare(rightReports == null ? 0 : rightReports.count(), leftReports == null ? 0 : leftReports.count());
                if (byCount != 0) return byCount;
                return Integer.compare(priorityRank(priorityLabel(rightReports)), priorityRank(priorityLabel(leftReports)));
            }
            int byPriority = Integer.compare(priorityRank(priorityLabel(rightReports)), priorityRank(priorityLabel(leftReports)));
            if (byPriority != 0) return byPriority;
            int byCount = Integer.compare(rightReports == null ? 0 : rightReports.count(), leftReports == null ? 0 : leftReports.count());
            if (byCount != 0) return byCount;
            return Long.compare(left.timestamp(), right.timestamp());
        });
        if (messages.size() > amount) return new ArrayList<>(messages.subList(0, amount));
        return messages;
    }

    private boolean isUrgentReport(MessageReports reports) {
        String priority = priorityLabel(reports);
        return "URGENT".equals(priority) || "HIGH".equals(priority);
    }

    private String priorityLabel(MessageReports reports) {
        if (reports == null) return "MEDIUM";
        Report.Priority priority = reports.highestPriority();
        if (priority == Report.Priority.URGENT) return "URGENT";
        if (priority == Report.Priority.HIGH) return "HIGH";
        if (priority == Report.Priority.LOW) return "LOW";
        return "MEDIUM";
    }

    private Report.Type topReportType(MessageReports reports) {
        if (reports == null) return Report.Type.OTHER;
        int spam = 0, harassment = 0, hateSpeech = 0, violence = 0, other = 0;
        for (Iterator<Report> it = reports.all(); it.hasNext(); ) {
            Report report = it.next();
            for (Report.Type type : report.types()) {
                if (type == Report.Type.SPAM) spam++;
                else if (type == Report.Type.HARASSMENT) harassment++;
                else if (type == Report.Type.HATE_SPEECH) hateSpeech++;
                else if (type == Report.Type.VIOLENCE) violence++;
                else other++;
            }
        }
        Report.Type best = Report.Type.OTHER;
        int bestCount = other;
        int bestRank = 1;
        if (spam > bestCount || (spam == bestCount && 2 > bestRank)) { best = Report.Type.SPAM; bestCount = spam; bestRank = 2; }
        if (harassment > bestCount || (harassment == bestCount && 3 > bestRank)) { best = Report.Type.HARASSMENT; bestCount = harassment; bestRank = 3; }
        if (hateSpeech > bestCount || (hateSpeech == bestCount && 4 > bestRank)) { best = Report.Type.HATE_SPEECH; bestCount = hateSpeech; bestRank = 4; }
        if (violence > bestCount || (violence == bestCount && 5 > bestRank)) best = Report.Type.VIOLENCE;
        return best;
    }

    private int priorityRank(String priority) {
        if ("URGENT".equals(priority)) return 4;
        if ("HIGH".equals(priority)) return 3;
        if ("MEDIUM".equals(priority)) return 2;
        if ("LOW".equals(priority)) return 1;
        return 0;
    }

    private int priorityColor(String priority) {
        if ("URGENT".equals(priority)) return Color.rgb(200, 0, 0);
        if ("HIGH".equals(priority)) return Color.rgb(194, 65, 12);
        if ("LOW".equals(priority)) return Color.rgb(0, 135, 62);
        return Color.parseColor("#081F5C");
    }

    private View adminMessageReportInfo(MessageReports reports) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(reportCardBackground(Color.parseColor("#E7F1FF")));
        LinearLayout.LayoutParams params = blockParams();
        params.setMargins(0, dp(10), 0, dp(8));
        box.setLayoutParams(params);
        String priority = priorityLabel(reports);
        Report.Type type = topReportType(reports);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(labelBadge(priority, reportChipBackground(priorityColor(priority)), Color.WHITE));
        TextView typeBadge = labelBadge(type.label(), reportChipBackground(Color.WHITE), Color.rgb(59, 86, 162));
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        typeParams.setMargins(dp(8), 0, 0, 0);
        row.addView(typeBadge, typeParams);
        box.addView(row);
        TextView detail = new TextView(this);
        detail.setText(reports.count() + " report" + (reports.count() == 1 ? "" : "s") + " · flagged for admin review");
        detail.setTextSize(12);
        detail.setTextColor(Color.parseColor("#425A88"));
        detail.setPadding(0, dp(8), 0, 0);
        applyTimes(detail, Typeface.BOLD);
        box.addView(detail);
        return box;
    }

    private TextView labelBadge(String text, Drawable background, int textColor) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextSize(11);
        badge.setTextColor(textColor);
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        badge.setBackground(background);
        applyTimes(badge, Typeface.BOLD);
        return badge;
    }

    private GradientDrawable reportCardBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), Color.parseColor("#D8E6FF"));
        return drawable;
    }

    private GradientDrawable reportChipBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(10));
        return drawable;
    }

    private GradientDrawable cardBackground(boolean highlighted) {
        GradientDrawable drawable = new GradientDrawable();

        if (highlighted) {
            drawable.setColor(Color.parseColor("#D0E3FF"));
            drawable.setStroke(dp(1), Color.parseColor("#A9CCFA"));
        } else {
            drawable.setColor(Color.WHITE);
            drawable.setStroke(dp(1), Color.parseColor("#D8E6FF"));
        }

        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable dockBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#7096D1"));
        float radius = dp(20);
        drawable.setCornerRadii(new float[] {
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
        });
        return drawable;
    }

    private GradientDrawable fabBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#081F5C"));
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(2), Color.rgb(215, 232, 251));
        return drawable;
    }

    private View dividerLine() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)));
        divider.setBackgroundColor(Color.parseColor("#E8EEF8"));
        return divider;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(Color.rgb(66, 90, 136));
        applyTimes(label, Typeface.BOLD);
        return label;
    }

    private GradientDrawable inputFieldBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), Color.parseColor("#D8E6FF"));
        return drawable;
    }

    private GradientDrawable postActionBackground(boolean enabled) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(enabled ? Color.parseColor("#081F5C") : Color.parseColor("#C7D8F2"));
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private GradientDrawable signOutBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#334EAC"));
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private GradientDrawable adminBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#081F5C"));
        drawable.setCornerRadius(dp(10));
        return drawable;
    }

    private GradientDrawable hiddenBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#F7DDDD"));
        drawable.setCornerRadius(dp(10));
        return drawable;
    }

    private GradientDrawable detailActionBackground(int backgroundColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(backgroundColor);
        drawable.setCornerRadius(dp(20));
        return drawable;
    }

    private GradientDrawable replyChipBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#E7F1FF"));
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private GradientDrawable tabBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? Color.parseColor("#081F5C") : Color.parseColor("#E7F1FF"));
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private GradientDrawable tagBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? Color.parseColor("#334EAC") : Color.parseColor("#E7F1FF"));
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), selected ? Color.parseColor("#334EAC") : Color.parseColor("#C7D8F2"));
        return drawable;
    }

    private GradientDrawable displayCategoryBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#F9FCFF"));
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private GradientDrawable replyInputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#EEF4FF"));
        drawable.setCornerRadius(dp(24));
        drawable.setStroke(dp(1), Color.parseColor("#D8E6FF"));
        return drawable;
    }

    private GradientDrawable sendButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#334EAC"));
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    private TextView avatarView(User user, String fallbackName, int sizeDp) {
        TextView avatar = new TextView(this);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextSize(sizeDp >= 32 ? 18 : 16);
        avatar.setBackground(avatarBackground(user));
        avatar.setText(avatarLetter(user, fallbackName));
        avatar.setTextColor(paletteColor(user, fallbackName));
        applyTimes(avatar, Typeface.BOLD);
        avatar.getPaint().setFakeBoldText(true);
        avatar.setTextScaleX(1.08f);
        return avatar;
    }

    private GradientDrawable avatarBackground(User user) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setShape(GradientDrawable.OVAL);
        if (user != null && user.role() == User.Role.Admin) {
            drawable.setStroke(dp(3), Color.parseColor("#081F5C"));
        } else {
            drawable.setStroke(dp(2), Color.parseColor("#C7D8F2"));
        }
        return drawable;
    }

    private String avatarLetter(User user, String fallbackName) {
        String source = user != null && user.username() != null && !user.username().isEmpty()
                ? user.username()
                : fallbackName;
        if (source == null || source.isEmpty()) return "?";
        return String.valueOf(Character.toUpperCase(source.charAt(0)));
    }

    private int paletteColor(User user, String fallbackName) {
        int[] palette = new int[] {
                Color.parseColor("#B42318"),
                Color.parseColor("#C2410C"),
                Color.parseColor("#A16207"),
                Color.parseColor("#15803D"),
                Color.parseColor("#0F766E"),
                Color.parseColor("#334EAC"),
                Color.parseColor("#7C3AED"),
                Color.parseColor("#BE185D")
        };
        String key;
        if (user != null && user.id() != null) {
            key = user.id().toString();
        } else if (fallbackName != null && !fallbackName.isEmpty()) {
            key = fallbackName;
        } else {
            key = "default";
        }
        return palette[Math.floorMod(key.hashCode(), palette.length)];
    }

    private void enableClipboard(EditText editText) {
        editText.setLongClickable(true);
        editText.setTextIsSelectable(true);
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setCursorVisible(true);
    }

    private void applyTimes(TextView view, int style) {
        view.setTypeface(Typeface.create("times new roman", style));
    }

    private void setPage(View view) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(view);
        root.removeAllViews();
        root.addView(scrollView);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class FlowLayout extends ViewGroup {
        private final int horizontalGap;
        private final int verticalGap;

        FlowLayout(android.content.Context context, int horizontalGap, int verticalGap) {
            super(context);
            this.horizontalGap = horizontalGap;
            this.verticalGap = verticalGap;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxWidth = Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
            int lineWidth = 0;
            int lineHeight = 0;
            int totalHeight = getPaddingTop() + getPaddingBottom();
            int usedWidth = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, totalHeight);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                int nextWidth = lineWidth == 0 ? childWidth : lineWidth + horizontalGap + childWidth;
                if (lineWidth > 0 && nextWidth > maxWidth) {
                    totalHeight += lineHeight + verticalGap;
                    usedWidth = Math.max(usedWidth, lineWidth);
                    lineWidth = childWidth;
                    lineHeight = childHeight;
                } else {
                    lineWidth = nextWidth;
                    lineHeight = Math.max(lineHeight, childHeight);
                }
            }

            totalHeight += lineHeight;
            usedWidth = Math.max(usedWidth, lineWidth);
            int measuredWidth = resolveSize(usedWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
            int measuredHeight = resolveSize(totalHeight, heightMeasureSpec);
            setMeasuredDimension(measuredWidth, measuredHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int maxWidth = right - left - getPaddingLeft() - getPaddingRight();
            int x = getPaddingLeft();
            int y = getPaddingTop();
            int lineHeight = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int requiredWidth = childWidth + lp.leftMargin + lp.rightMargin;
                if (x > getPaddingLeft() && x + requiredWidth > maxWidth + getPaddingLeft()) {
                    x = getPaddingLeft();
                    y += lineHeight + verticalGap;
                    lineHeight = 0;
                }

                int childLeft = x + lp.leftMargin;
                int childTop = y + lp.topMargin;
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                x += requiredWidth + horizontalGap;
                lineHeight = Math.max(lineHeight, childHeight + lp.topMargin + lp.bottomMargin);
            }
        }

        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }

        @Override
        protected LayoutParams generateLayoutParams(LayoutParams p) {
            return new MarginLayoutParams(p);
        }

        @Override
        public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
            return new MarginLayoutParams(getContext(), attrs);
        }

        @Override
        protected boolean checkLayoutParams(LayoutParams p) {
            return p instanceof MarginLayoutParams;
        }
    }

    private UUID postIdFromIntent(Intent intent) {
        if (intent == null) return null;
        String value = intent.getStringExtra(EXTRA_POST_ID);
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean showPendingPostIfAny() {
        if (pendingPostId == null) return false;
        Post post = PostDAO.getInstance().get(new Post(pendingPostId));
        pendingPostId = null;
        if (post == null || !canViewPost(post)) {
            toast("That post is no longer available");
            return false;
        }
        showPostDetail(post);
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_REPLIES,
                "Post replies",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Alerts when a watched post receives a reply.");
        channel.setShowBadge(true);
        channel.enableLights(true);
        channel.setLightColor(UNREAD_BADGE_RED);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 42);
    }

    private void scheduleDemoReplyNotification() {
        if (currentUser == null) return;
        notificationHandler.postDelayed(() -> {
            if (inForeground || currentUser == null) return;
            Post post = newestPostForNotification();
            if (post == null) return;

            User helper = ensureDemoReplier();
            Message reply = new Message(
                    UUID.randomUUID(),
                    helper.id(),
                    post.id,
                    System.currentTimeMillis(),
                    "I added a quick update while you were away.");
            if (!post.messages.insert(reply)) return;

            saveInBackground(null);
            showReplyNotification(post, reply);
        }, 4000);
    }

    private Post newestPostForNotification() {
        boolean isAdmin = currentUser.role() == User.Role.Admin;
        Post newestOwn = null;
        Post newestFallback = null;
        long newestOwnTimestamp = Long.MIN_VALUE;
        long newestFallbackTimestamp = Long.MIN_VALUE;
        for (Iterator<Post> it = PostDAO.getInstance().getAll(); it.hasNext(); ) {
            Post post = it.next();
            if (!canViewPost(post)) continue;
            long timestamp = postTimestamp(post, isAdmin);
            if (post.poster != null && post.poster.equals(currentUser.id())) {
                if (timestamp > newestOwnTimestamp) {
                    newestOwnTimestamp = timestamp;
                    newestOwn = post;
                }
            }
            if (timestamp > newestFallbackTimestamp) {
                newestFallbackTimestamp = timestamp;
                newestFallback = post;
            }
        }
        return newestOwn != null ? newestOwn : newestFallback;
    }

    private User ensureDemoReplier() {
        User existing = UserDAO.getInstance().get(new User("CampusHelper"));
        if (existing != null) return existing;
        User helper = new User(UUID.randomUUID(), User.Role.Member, "CampusHelper", "helper123");
        UserDAO.getInstance().add(helper);
        return helper;
    }

    private void showReplyNotification(Post post, Message reply) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_POST_ID, post.id.toString());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, post.id.hashCode(), intent, pendingIntentFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_REPLIES)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.sym_action_chat)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo))
                .setColor(UNREAD_BADGE_RED)
                .setContentTitle("New Reply on AnuShare.")
                .setContentText(reply.message())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(post.id.hashCode(), builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        notificationHandler.removeCallbacksAndMessages(null);
        diskExecutor.shutdown();
    }
}