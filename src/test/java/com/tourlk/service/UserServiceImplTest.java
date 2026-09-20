package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.UpdateProfileRequestDto;
import com.tourlk.dto.UserResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.UserStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.repo.UserRepository;
import com.tourlk.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the profile and admin user-management parts of
 * {@link UserServiceImpl}. Repository and JwtUtil mocked.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl service;

    private User tourist;
    private User admin;

    @BeforeEach
    void setUp() {
        tourist = User.builder().id(1L).name("Tess").email("tess@example.com")
                .role(Role.TOURIST).status(UserStatus.ACTIVE).build();
        admin = User.builder().id(9L).name("Amy").email("amy@example.com")
                .role(Role.ADMIN).status(UserStatus.ACTIVE).build();
    }

    // ------------------------------------------------------------------
    // updateProfile
    // ------------------------------------------------------------------

    @Test
    void updateProfile_newEmail_savesTrimmedValuesAndReturnsFreshToken() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken("new@example.com", "TOURIST")).thenReturn("fresh-token");

        AuthResponseDto result = service.updateProfile(tourist,
                new UpdateProfileRequestDto("  Tess T  ", " new@example.com ", "   "));

        assertThat(result.getName()).isEqualTo("Tess T");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getToken()).isEqualTo("fresh-token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(tourist.getPhone()).isNull();
    }

    @Test
    void updateProfile_emailBelongsToAnotherAccount_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("amy@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateProfile(tourist,
                new UpdateProfileRequestDto("Tess", "amy@example.com", null)))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_keepingOwnEmail_isAllowed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("tess@example.com")).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken("tess@example.com", "TOURIST")).thenReturn("tok");

        AuthResponseDto result = service.updateProfile(tourist,
                new UpdateProfileRequestDto("Tess", "tess@example.com", "0771234567"));

        assertThat(result.getEmail()).isEqualTo("tess@example.com");
        assertThat(tourist.getPhone()).isEqualTo("0771234567");
    }

    // ------------------------------------------------------------------
    // deactivate / reactivate
    // ------------------------------------------------------------------

    @Test
    void deactivateUser_ownAccount_throwsBadRequest() {
        assertThatThrownBy(() -> service.deactivateUser(9L, admin))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_otherAccount_marksDeactivated() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponseDto result = service.deactivateUser(1L, admin);

        assertThat(result.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void reactivateUser_marksActive() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.reactivateUser(1L).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ------------------------------------------------------------------
    // deleteUser
    // ------------------------------------------------------------------

    @Test
    void deleteUser_ownAccount_throwsBadRequest() {
        assertThatThrownBy(() -> service.deleteUser(9L, admin))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteUser_stillActive_throwsInvalidStatusTransition() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));

        assertThatThrownBy(() -> service.deleteUser(1L, admin))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_deactivated_deletes() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));

        service.deleteUser(1L, admin);

        verify(userRepository).delete(tourist);
        verify(userRepository).flush();
    }

    @Test
    void deleteUser_referencedByOtherRecords_throwsBadRequest() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        doThrow(new DataIntegrityViolationException("FK")).when(userRepository).flush();

        assertThatThrownBy(() -> service.deleteUser(1L, admin))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be deleted");
    }

    // ------------------------------------------------------------------
    // searchUsers
    // ------------------------------------------------------------------

    @Test
    void searchUsers_blankTerm_listsEveryone() {
        when(userRepository.findAll(any(Sort.class))).thenReturn(List.of(tourist, admin));

        assertThat(service.searchUsers("  ")).hasSize(2);
    }

    @Test
    void searchUsers_term_searchesNameOrEmail() {
        when(userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                any(), any(), any(Sort.class))).thenReturn(List.of(tourist));

        List<UserResponseDto> result = service.searchUsers(" tess ");

        assertThat(result).extracting(UserResponseDto::getEmail).containsExactly("tess@example.com");
    }
}
