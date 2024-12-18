package com.sparta.haengye_project.user.service;


import com.sparta.haengye_project.user.dto.UserSignupRequestDto;
import com.sparta.haengye_project.user.dto.UserSignupResponseDto;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;



    public UserSignupResponseDto signup(UserSignupRequestDto requestDto) {

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        Optional<User> existEmail = userRepository.findByEmail(requestDto.getEmail());

        if (existEmail.isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        User user = new User();
        user.setEmail(requestDto.getEmail());
        user.setPassword(encodedPassword);
        user.setName(requestDto.getName());
        user.setPhoneNumber(requestDto.getPhoneNumber());
        user.setAddress(requestDto.getAddress());

        // User 저장
        User savedUser = userRepository.save(user);

        return new UserSignupResponseDto(savedUser.getUserId(), savedUser.getEmail(), savedUser.getName());

    }
}
