package com.example.service.user;

import com.example.repository.UserRepository;
import com.example.user.dto.UserDTO;
import com.example.user.entity.Role;
import com.example.user.entity.UserEntity;
import com.example.utils.UserMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(userEntity.getRole().name()));
        return new User(userEntity.getUsername(), userEntity.getPassword(), authorities);
    }

    public Optional<UserDTO> findByUsername(String username) {
        return userRepo.findByUsername(username).map(userMapper::UserEntityToUserDTO);
    }

    public Optional<UserDTO> findByEmail(String email) {
        return userRepo.findByEmail(email).map(userMapper::UserEntityToUserDTO);
    }

    public void createNewUser(UserDTO userDTO) {
        UserEntity userEntity = userMapper.UserDTOToUserEntity(userDTO);
        userEntity.setPassword(passwordEncoder.encode(userDTO.password()));
        userRepo.saveAndFlush(userEntity);
    }
}