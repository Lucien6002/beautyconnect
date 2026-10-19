package com.beautyconnect.service;

import com.beautyconnect.dto.PersonalInfoForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private PersonalInfoForm form(String first, String last, String phone) {
        PersonalInfoForm form = new PersonalInfoForm();
        form.setFirstName(first);
        form.setLastName(last);
        form.setPhone(phone);
        return form;
    }

    @Test
    void updatePersonalInfo_ShouldChangeNameAndPhoneButNotEmailOrRole() {
        User user = User.builder().id(1L).email("a@b.fr").firstName("Old").lastName("Name")
                .role(Role.CLIENT).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User saved = userService.updatePersonalInfo(1L, form("  Lea ", " Martin ", " 06 12 34 56 78 "));

        assertEquals("Lea", saved.getFirstName());
        assertEquals("Martin", saved.getLastName());
        assertEquals("06 12 34 56 78", saved.getPhone());
        assertEquals("a@b.fr", saved.getEmail());
        assertEquals(Role.CLIENT, saved.getRole());
    }

    @Test
    void updatePersonalInfo_ShouldClearPhone_WhenBlank() {
        User user = User.builder().id(1L).firstName("A").lastName("B").phone("0600000000").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertNull(userService.updatePersonalInfo(1L, form("A", "B", "  ")).getPhone());
    }

    @Test
    void changePassword_ShouldEncodeNewPassword_WhenCurrentIsCorrect() {
        User user = User.builder().id(1L).password("oldHash").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old12345", "oldHash")).thenReturn(true);
        when(passwordEncoder.matches("new12345", "oldHash")).thenReturn(false);
        when(passwordEncoder.encode("new12345")).thenReturn("newHash");

        userService.changePassword(1L, "old12345", "new12345");

        assertEquals("newHash", user.getPassword());
    }

    @Test
    void changePassword_ShouldReject_WhenCurrentIsWrong() {
        User user = User.builder().id(1L).password("oldHash").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "oldHash")).thenReturn(false);

        assertThrows(IllegalOperationException.class, () -> userService.changePassword(1L, "bad", "new12345"));
        assertEquals("oldHash", user.getPassword());
    }

    @Test
    void changePassword_ShouldReject_WhenNewEqualsCurrent() {
        User user = User.builder().id(1L).password("oldHash").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("same1234", "oldHash")).thenReturn(true);

        assertThrows(IllegalOperationException.class, () -> userService.changePassword(1L, "same1234", "same1234"));
        assertEquals("oldHash", user.getPassword());
    }

    @Test
    void updatePersonalInfo_ShouldThrow_WhenUserDoesNotExist() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.updatePersonalInfo(9L, form("A", "B", null)));
    }
}
