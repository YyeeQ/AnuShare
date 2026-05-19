package com.example.moderationapp.data.model;

import java.util.UUID;

public record Report(UUID message, UUID user, long timestamp) {}
