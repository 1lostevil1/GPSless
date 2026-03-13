package com.example.service.user;

import com.example.repository.UserRepository;
import com.example.user.dto.UserDTO;
import com.example.user.entity.UserEntity;
import com.example.utils.UserMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {


    private final UserRepository userRepo;

    private  final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepo.findByUsername(username).orElseThrow(()->new UsernameNotFoundException(
                String.format("User '%s' not found",username)
        ));
        return new User(userEntity.getUsername(),userEntity.getPassword(),new ArrayList<>());

    }

    public Optional<UserDTO> findByUsername(String username){
        return userRepo.findByUsername(username).map(userMapper::UserEntityToUserDTO);
    }

    public Optional<UserDTO> findByEmail(String email){
        return userRepo.findByEmail(email).map(userMapper::UserEntityToUserDTO);
    }


    public void createNewUser(UserDTO userDTO) {

        UserDTO userWithEncodedPassword = new UserDTO(userDTO.username(), userDTO.email(), passwordEncoder.encode(userDTO.password()));
        userRepo.saveAndFlush(userMapper.UserDTOToUserEntity(userWithEncodedPassword));
    }
}
