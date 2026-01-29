package com.moro.SpringSecurityApp.controllers;

import com.moro.SpringSecurityApp.dto.AuthenticationDTO;
import com.moro.SpringSecurityApp.dto.PersonDTO;
import com.moro.SpringSecurityApp.models.Person;
import com.moro.SpringSecurityApp.security.JWTUtil;
import com.moro.SpringSecurityApp.services.RegistrationService;
import com.moro.SpringSecurityApp.util.PersonValidator;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final PersonValidator personValidator;
    private final RegistrationService registrationService;
    private final JWTUtil jwtUtil;
    private final ModelMapper modelMapper;
    private final AuthenticationManager authenticationManager;
    @Autowired
    public AuthController(PersonValidator personValidator, RegistrationService registrationService, JWTUtil jwtUtil, ModelMapper modelMapper, AuthenticationManager authenticationManager) {
        this.personValidator = personValidator;
        this.registrationService = registrationService;
        this.jwtUtil = jwtUtil;
        this.modelMapper = modelMapper;
        this.authenticationManager = authenticationManager;
    }
// Методы для работы с сессиями и куки
//    @GetMapping("/login")
//    public String loginPage() {
//        return "auth/login";
//    }
//
//    @GetMapping("/registration")
//    public String registrationPage(@ModelAttribute("person") Person person) {
//        return "auth/registration";
//    }


    @PostMapping("/registration")
    public Map<String, String> performRegistration(@RequestBody @Valid PersonDTO personDTO,
                                      BindingResult bindingResult) {
        Person person = convertToPerson(personDTO);
        personValidator.validate(person, bindingResult);
        // Если есть ошибки, то возвращаем обратно на страницу
        if (bindingResult.hasErrors()) {
            // Просто для быстроты делаем таким образом, вообще надо выбросить исключение
            // А затем обработать с помощью @ExceptionHandler
            // И отправить JSON c ошибкой
            return Map.of("message","Ошибка");
        }
        registrationService.register(person);

        // После регистрации Spring сам отправлял пользователю куки, а на сервере создавал сессию
//        return "redirect:/auth/login";
        // В случае с JWT мы должны сгенерировать токен и отправить его пользователю
        String token = jwtUtil.generateToken(person.getUsername());
        return Map.of("jwt-token", token);
    }
    @PostMapping("/login")
    public Map<String, String> performLogin(@RequestBody AuthenticationDTO authenticationDTO) {
        UsernamePasswordAuthenticationToken authInputToken =
                new UsernamePasswordAuthenticationToken(authenticationDTO.getUsername(),authenticationDTO.getPassword());
        try {
            authenticationManager.authenticate(authInputToken);
        } catch (BadCredentialsException e) {
            return Map.of("message", "Incorrect credentials");
        }
        String token = jwtUtil.generateToken(authenticationDTO.getUsername());
        return Map.of("jwt-token", token);

    }

    private Person convertToPerson(PersonDTO personDTO) {
        return modelMapper.map(personDTO, Person.class);
    }
}
