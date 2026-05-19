package com.example.moderationapp.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
        LinearLayout content = page("Posts");
        content.addView(smallText(currentUser.username() + " · " + currentUser.role()));

        if (currentUser.role() == User.Role.Admin) {
            Button admin = secondaryButton("Admin panel");
            admin.setOnClickListener(v -> showAdminPanel());
            content.addView(admin);
        }

        List<Post> posts = new ArrayList<>();
        for (Iterator<Post> it = PostDAO.getInstance().getAll(); it.hasNext(); ) {
            posts.add(it.next());
        }
        posts.sort((left, right) -> Long.compare(latestActivity(right), latestActivity(left)));

        for (Post post : posts) {
            Button row = secondaryButton(post.topic);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setOnClickListener(v -> showPostDetail(post));
            content.addView(row);
        }

        Button createPost = primaryButton("Create post");
        createPost.setOnClickListener(v -> showCreatePost());
        content.addView(createPost);

        Button signOut = secondaryButton("Sign out");
        signOut.setOnClickListener(v -> {
            currentUser = null;
            showLogin();
        });
        content.addView(signOut);
        setPage(content);
    }

    private void showCreatePost() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(20));
        content.setBackgroundColor(Color.rgb(247, 248, 250));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setLayoutParams(blockParams());

        Button cancel = secondaryButton("Cancel");
        cancel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0f));
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
        title.setTextColor(Color.rgb(21, 23, 26));
        topBar.addView(title);

        Button post = compactButton("Post");
        post.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0f));
        setActionButtonEnabled(post, false);
        topBar.addView(post);

        content.addView(topBar);
        content.addView(smallText(currentUser.username()));

        EditText postTitle = input("Title", false);
        content.addView(postTitle);

        EditText postContent = new EditText(this);
        postContent.setHint("Content");
        postContent.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        postContent.setSingleLine(false);
        postContent.setMinLines(6);
        postContent.setGravity(Gravity.TOP | Gravity.START);
        postContent.setLayoutParams(blockParams());
        content.addView(postContent);

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

        setPage(content);
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

    private void setActionButtonEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(enabled
                ? Color.rgb(36, 87, 214)
                : Color.rgb(170, 176, 186));
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

    private long latestActivity(Post post) {
        long latest = Long.MIN_VALUE;
        for (Iterator<Message> it = post.messages.getAll(); it.hasNext(); ) {
            latest = Math.max(latest, it.next().timestamp());
        }
        return latest;
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
