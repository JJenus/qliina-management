package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.common.seed.DevDataSeeder;
import com.jjenus.qliina_management.identity.dto.DevTestUserDTO;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Dev-only list of the demo tenant's seeded login accounts, so the login page
 * can render one-click buttons. Registered only on the dev/test/seed profiles:
 * no bean exists in production, so the path 404s there and exposes nothing.
 *
 * <p>The list is derived from the database (demo business by slug) rather than
 * hardcoded, so adding or renaming a seeded user in {@link DevDataSeeder}
 * requires no frontend change. Every demo user shares
 * {@link DevDataSeeder#DEMO_PASSWORD}.
 */
@RestController
@RequestMapping("/api/v1/public/dev/test-users")
@Profile({"dev", "test", "seed"})
@RequiredArgsConstructor
public class DevTestUsersController {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    @GetMapping
    public List<DevTestUserDTO> list() {
        return businessRepository.findBySlug(DevDataSeeder.DEMO_SLUG)
                .map(this::demoUsers)
                .orElseGet(List::of);
    }

    private List<DevTestUserDTO> demoUsers(Business demo) {
        return userRepository.findAllByBusinessId(demo.getId()).stream()
                .sorted(Comparator.comparing(User::getUsername))
                .map(u -> DevTestUserDTO.builder()
                        .username(u.getUsername())
                        .password(DevDataSeeder.DEMO_PASSWORD)
                        .displayName((u.getFirstName() + " " + u.getLastName()).trim())
                        .roles(u.getRoles().stream()
                                .map(ur -> ur.getRole().getName())
                                .sorted()
                                .toList())
                        .build())
                .toList();
    }
}