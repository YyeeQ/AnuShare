package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.User;
import java.util.Iterator;
import java.util.UUID;

public class UserDAO extends DAO<User> {
    private static UserDAO instance;

    public static UserDAO getInstance() {
        if (instance == null) instance = new UserDAO();
        return instance;
    }

    private UserDAO() {
        super((o1, o2) -> o1.username().compareToIgnoreCase(o2.username()));
    }

    public User login(String username, String password) {
        User user = data.get(new User(username));
        return (user != null && user.password().equals(password)) ? user : null;
    }

    public User register(String username, String password) {
        for (char c : username.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) return null;
        }
        if (username.length() < 4 || username.length() > 20) return null;
        if (password.length() < 4) return null;

        User existingUser = data.get(new User(username));
        if (existingUser != null) return null;

        User newUser = new User(UUID.randomUUID(), User.Role.Member, username, password);
        return data.insert(newUser) ? newUser : null;
    }

    public User getByUUID(UUID id) {
        for (Iterator<User> it = data.getAll(); it.hasNext(); ) {
            User user = it.next();
            if (user.getUUID().equals(id)) return user;
        }
        return null;
    }
}
