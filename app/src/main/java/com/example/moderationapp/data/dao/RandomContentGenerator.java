package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.User;

import java.util.Random;
import java.util.UUID;

public class RandomContentGenerator {
    private static final String[] NAMES = new String[] {"Alexandria", "Beatrice", "Carmen", "Diego", "Ema", "Farah", "Georg", "Hadrian"};
    private static final String[] TOPICS = new String[] {"UML", "design patterns", "data structures", "persistent data",
            "modelling", "software construction", "exam", "mini project", "group project", "singleton", "observer",
            "factory", "strategy", "state", "facade", "DAO", "IntelliJ", "Android Studio", "AVL tree", "tree balancing",
            "concurrency"};
    private static final String[] POST_NAMES = new String[] {"Question about %s", "I love %s", "Study session: %s",
            "Practicing %s", "I don't understand %s", "Applications of %s", "How to implement %s?"};

    public static void populateRandomData() {
        for (int i = 0; i < 200; i++) {
            generateUser();
        }
        for (int i = 0; i < 50; i++) {
            generatePost();
        }
        for (int i = 0; i < 25_000; i++) {
            generateComment();
        }
    }

    private static final Random random = new Random();

    private static <T> T chooseRandomFromArray(T[] array) {
        if (array == null || array.length == 0)
            throw new RuntimeException("Targeted array is empty");
        return array[random.nextInt(array.length)];
    }

    public static void generateUser() {
        String username = chooseRandomFromArray(NAMES) + random.nextInt(1000);
        User.Role role = random.nextInt(4) == 0 ? User.Role.Admin : User.Role.Member;
        User user = new User(UUID.randomUUID(), role, username, generatePassword());
        UserDAO.getInstance().add(user);
    }

    private static String generatePassword() {
        return "password" + random.nextInt(10000);
    }

    public static void generatePost() {
        User user = UserDAO.getInstance().getRandom();
        if (user == null) return;
        String postName = String.format(chooseRandomFromArray(POST_NAMES), chooseRandomFromArray(TOPICS));
        Post post = new Post(UUID.randomUUID(), user.getUUID(), postName);
        PostDAO.getInstance().add(post);
    }

    public static void generateComment() {
        generateComment(PostDAO.getInstance().getRandom());
    }

    public static void generateComment(Post post) {
        User user = UserDAO.getInstance().getRandom();
        if (post == null || user == null) return;

        String content = String.format("Hello from %s", user.username());

        long maxAgeMs = 7L * 24L * 60L * 60L * 1000L;
        long timestamp = System.currentTimeMillis() - Math.abs(random.nextLong() % maxAgeMs);

        Message message = new Message(UUID.randomUUID(), user.getUUID(), post.getUUID(), timestamp, content);
        post.messages.insert(message);
    }
}
