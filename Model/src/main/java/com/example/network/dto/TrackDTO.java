package com.example.network.dto;

import com.example.user.entity.Role;

import java.util.List;

public record TrackDTO(List<NetworkSnapshot> track, Role role) {
}
