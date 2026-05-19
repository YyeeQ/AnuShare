package com.example.moderationapp.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.RandomContentGenerator;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.MessageReports;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.data.persistence.DataManager;
import com.example.moderationapp.data.persistence.io.AndroidIOFactory;
import com.example.moderationapp.logic.moderation.ModerationTools;
import com.example.moderationapp.logic.util.TimeFormatter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private FrameLayout root;
    private User currentUser;
    private final DataManager dataManager = DataManager.getInstance();
    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataManager.configureIO(new AndroidIOFactory(getApplicationContext()));
        
        root = new FrameLayout(this);
        setContentView(root);
        
        // Load data in background
        diskExecutor.execute(() -> {
            dataManager.readAll();
            seedDemoDataIfEmpty();
            runOnUiThread(this::showLogin);
        });
    }

    private void saveInBackground(Runnable onDone) {
        diskExecutor.execute(() -> {
            dataManager.writeAll();
            if (onDone != null) runOnUiThread(onDone);
        });
    }

    private void showLogin() {
        LinearLayout form = page("Sign in");
        EditText username = input("Username", false);
        EditText password = input("Password", true);
        form.addView(username);
        form.addView(password);

        Button signIn = primaryButton("Sign in");
        signIn.setOnClickListener(v -> {
            User user = UserDAO.getInstance().login(username.getText().toString().trim(), password.getText().toString());
            if (user == null) {
                toast("Invalid username or password");
                return;
            }
            currentUser = user;
            showPostList();
        });
        form.addView(signIn);

        Button register = secondaryButton("Create account");
        register.setOnClickListener(v -> showRegister());
        form.addView(register);
        setPage(form);
    }

    private void showRegister() {
        LinearLayout form = page("Create account");
        EditText username = input("Username", false);
        EditText password = input("Password", true);
        form.addView(username);
        form.addView(password);

        Button create = primaryButton("Register");
        create.setOnClickListener(v -> {
            User user = UserDAO.getInstance().register(username.getText().toString().trim(), password.getText().toString());
            if (user == null) {
                toast("Username or password is not valid");
                return;
            }
            currentUser = user;
            saveInBackground(this::showPostList);
        });
        form.addView(create);

        Button back = secondaryButton("Back to sign in");
        back.setOnClickListener(v -> showLogin());
        form.addView(back);
        setPage(form);
    }

    private void showPostList() {
        boolean isAdmin = currentUser.role() == User.Role.Admin;

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.rgb(245, 247, 251));

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
            Button admin = secondaryButton("Admin panel");
            applyTimes(admin, Typeface.BOLD);
            admin.setOnClickListener(v -> showAdminPanel());
            listSection.addView(admin);
        }

        List<Post> posts = new ArrayList<>();
        for (Iterator<Post> it = PostDAO.getInstance().getAll(); it.hasNext(); ) {
            posts.add(it.next());
        }
        posts.sort((left, right) -> Long.compare(postTimestamp(right, isAdmin), postTimestamp(left, isAdmin)));

        if (posts.isEmpty()) {
            TextView empty = bodyText("No posts yet. Tap the button below to create the first one.");
            applyTimes(empty, Typeface.NORMAL);
            empty.setPadding(0, dp(40), 0, 0);
            listSection.addView(empty);
        } else {
            for (Post post : posts) {
                listSection.addView(postCard(post, isAdmin));
            }
        }

        content.addView(listSection);

        scrollView.addView(content);
        screen.addView(scrollView);
        screen.addView(postComposerDock());

        root.removeAllViews();
        root.addView(screen);
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
        LinearLayout content = page(post.topic);
        Button back = secondaryButton("Back");
        back.setOnClickListener(v -> showPostList());
        content.addView(back);

        boolean isAdmin = currentUser.role() == User.Role.Admin;
        for (Iterator<Message> it = post.getVisibleMessages(isAdmin).getAll(); it.hasNext(); ) {
            Message message = it.next();
            content.addView(messageView(message, () -> showPostDetail(post)));
        }
        setPage(content);
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
        username.setText("Hello, " + currentUser.username());
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
        Message previewMessage = previewMessage(post, isAdmin);
        long timestamp = postTimestamp(post, isAdmin);
        int replyCount = replyCount(post, isAdmin);
        User authorUser = UserDAO.getInstance().getByUUID(post.poster);
        boolean adminPost = authorUser != null && authorUser.role() == User.Role.Admin;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(blockParams());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardBackground(adminPost));
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
        applyTimes(time, Typeface.NORMAL);
        metaRow.addView(time);
        card.addView(metaRow);

        TextView title = new TextView(this);
        title.setText(post.topic);
        title.setTextSize(18);
        title.setTextColor(Color.rgb(21, 23, 26));
        title.setPadding(0, dp(6), 0, dp(8));
        applyTimes(title, Typeface.BOLD);
        card.addView(title);

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

        ImageView bubble = new ImageView(this);
        Drawable bubbleDrawable = getDrawable(android.R.drawable.sym_action_chat);
        if (bubbleDrawable != null) {
            bubbleDrawable = bubbleDrawable.mutate();
            bubbleDrawable.setTint(Color.parseColor("#C7D8F2"));
            bubble.setImageDrawable(bubbleDrawable);
        }
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        repliesRow.addView(bubble, bubbleParams);

        TextView replies = new TextView(this);
        replies.setText(replyCount + " comments");
        replies.setTextSize(13);
        replies.setTextColor(Color.rgb(93, 102, 117));
        applyTimes(replies, Typeface.NORMAL);
        LinearLayout.LayoutParams repliesParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        repliesParams.setMargins(dp(4), 0, 0, 0);
        repliesRow.addView(replies, repliesParams);
        card.addView(repliesRow);
        return card;
    }

    private View postComposerDock() {
        FrameLayout dock = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(108));
        params.gravity = Gravity.BOTTOM;
        dock.setLayoutParams(params);

        View bar = new View(this);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        barParams.gravity = Gravity.BOTTOM;
        bar.setLayoutParams(barParams);
        bar.setBackground(dockBackground());
        dock.addView(bar);

        Button createPost = new Button(this);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(dp(56), dp(56));
        buttonParams.gravity = Gravity.TOP | Gravity.END;
        buttonParams.topMargin = dp(28);
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

        return dock;
    }

    private void showAdminPanel() {
        LinearLayout content = page("Admin");
        Button back = secondaryButton("Back");
        back.setOnClickListener(v -> showPostList());
        content.addView(back);

        Iterator<Message> reported = ModerationTools.getReportedMessages("MOST", 20);
        while (reported.hasNext()) {
            Message message = reported.next();
            MessageReports reports = ReportDAO.getInstance().getReportsFor(message.id());
            content.addView(smallText((reports == null ? 0 : reports.count()) + " reports"));
            content.addView(messageView(message, this::showAdminPanel));
        }
        setPage(content);
    }

    private void seedDemoDataIfEmpty() {
        if (UserDAO.getInstance().getAll().hasNext()) return;
        UserDAO.getInstance().add(new User(UUID.randomUUID(), User.Role.Admin, "admin", "admin123"));
        UserDAO.getInstance().register("member", "member123");
        RandomContentGenerator.generatePost();
        RandomContentGenerator.generatePost();
        for (int i = 0; i < 8; i++) RandomContentGenerator.generateComment();
        dataManager.writeAll();
    }

    private LinearLayout page(String title) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(28), dp(20), dp(20));
        layout.setBackgroundColor(Color.rgb(247, 248, 250));
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
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
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

    private String displayAuthor(Post post) {
        User author = UserDAO.getInstance().getByUUID(post.poster);
        return author == null ? "Unknown user" : author.username();
    }

    private GradientDrawable cardBackground(boolean adminPost) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(adminPost ? "#D0E3FF" : "#E7F1FF"));
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
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private GradientDrawable adminBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#081F5C"));
        drawable.setCornerRadius(dp(10));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        diskExecutor.shutdown();
    }
}
