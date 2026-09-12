package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.LoginRequest;
import com.eexchange.backend.DTO.SignupRequest;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.Optional;

@Service
public class AuthService {
    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserRepository userRepository;
    @Autowired
    public AuthService(UserRepository userRepository){
        this.userRepository = userRepository;
    }
    public User signup(SignupRequest signupRequest){
        Optional<User> existing = userRepository.findByEmail(signupRequest.getEmail());
        if(existing.isPresent()){
            throw new RuntimeException("User already exists");
        }

        User user = new User();
        user.setEmail(signupRequest.getEmail());
        user.setName(signupRequest.getName());
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        return userRepository.save(user);
    }
    public User login(LoginRequest loginRequest){
        User user = userRepository.findByEmail(loginRequest.getEmail()).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.isBlocked()) {
            throw new RuntimeException("Your account has been blocked by an administrator");
        }

        if(!passwordEncoder.matches(loginRequest.getPassword(),user.getPassword())){
            throw new RuntimeException("Invalid User");
        }
        user.setPassword(null);
        //why?
        //Notice that you do not have the @Transactional annotation on this method or on the AuthService class itself.
        //
        //Because there is no active transaction encompassing this method,
        // the User object you fetched from the database is operating in a "detached" or non-transactional state
        // by the time you modify it. Since you never explicitly call userRepository.save(user),
        // Hibernate ignores the change, and the database is completely safe.


        //The Hidden Danger: "Dirty Checking"
        //Here is where things get dangerous. Hibernate has a feature called "Dirty Checking".
        //
        //If you ever added the @Transactional annotation to your AuthService
        // (which is a very common best practice for Service classes),
        // Hibernate's behavior completely changes
        //If you had @Transactional there, Hibernate would automatically execute
        //an UPDATE statement to set the password to null in the database as soon
        // as the method finished executing, even without you calling .save()!
        return user;
    }

    public User updateUser(String email, String newName) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        user.setName(newName);
        return userRepository.save(user);
    }

    public boolean userExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
