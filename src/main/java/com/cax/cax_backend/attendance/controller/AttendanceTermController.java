package com.cax.cax_backend.attendance.controller;

import com.cax.cax_backend.attendance.dto.CreateTermRequest;
import com.cax.cax_backend.attendance.dto.SubjectRequest;
import com.cax.cax_backend.attendance.dto.UpdateTermRequest;
import com.cax.cax_backend.attendance.model.AttendanceTerm;
import com.cax.cax_backend.attendance.service.AttendanceTermService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance/terms")
@RequiredArgsConstructor
public class AttendanceTermController {

    private final AttendanceTermService attendanceTermService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AttendanceTerm>>> listMine(Authentication auth) {
        String userId = requireUserId(auth);
        return ResponseEntity.ok(ApiResponse.success(attendanceTermService.listMine(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AttendanceTerm>> createTerm(
            Authentication auth,
            @RequestBody CreateTermRequest request) {
        String userId = requireUserId(auth);
        AttendanceTerm term = attendanceTermService.createTerm(userId, request);
        return ResponseEntity.status(201).body(ApiResponse.created("Term created", term));
    }

    @PutMapping("/{termId}")
    public ResponseEntity<ApiResponse<AttendanceTerm>> renameTerm(
            Authentication auth,
            @PathVariable String termId,
            @RequestBody UpdateTermRequest request) {
        String userId = requireUserId(auth);
        AttendanceTerm term = attendanceTermService.renameTerm(userId, termId, request);
        return ResponseEntity.ok(ApiResponse.success("Term updated", term));
    }

    @PutMapping("/{termId}/activate")
    public ResponseEntity<ApiResponse<AttendanceTerm>> activateTerm(
            Authentication auth,
            @PathVariable String termId) {
        String userId = requireUserId(auth);
        AttendanceTerm term = attendanceTermService.activateTerm(userId, termId);
        return ResponseEntity.ok(ApiResponse.success("Term activated", term));
    }

    @DeleteMapping("/{termId}")
    public ResponseEntity<ApiResponse<Void>> deleteTerm(
            Authentication auth,
            @PathVariable String termId) {
        String userId = requireUserId(auth);
        attendanceTermService.deleteTerm(userId, termId);
        return ResponseEntity.ok(ApiResponse.success("Term deleted"));
    }

    @PostMapping("/{termId}/subjects")
    public ResponseEntity<ApiResponse<AttendanceTerm>> addSubject(
            Authentication auth,
            @PathVariable String termId,
            @RequestBody SubjectRequest request) {
        String userId = requireUserId(auth);
        AttendanceTerm term = attendanceTermService.addSubject(userId, termId, request);
        return ResponseEntity.status(201).body(ApiResponse.created("Subject added", term));
    }

    @PutMapping("/{termId}/subjects/{subjectId}")
    public ResponseEntity<ApiResponse<AttendanceTerm>> updateSubject(
            Authentication auth,
            @PathVariable String termId,
            @PathVariable String subjectId,
            @RequestBody SubjectRequest request) {
        String userId = requireUserId(auth);
        AttendanceTerm term = attendanceTermService.updateSubject(userId, termId, subjectId, request);
        return ResponseEntity.ok(ApiResponse.success("Subject updated", term));
    }

    @DeleteMapping("/{termId}/subjects/{subjectId}")
    public ResponseEntity<ApiResponse<AttendanceTerm>> deleteSubject(
            Authentication auth,
            @PathVariable String termId,
            @PathVariable String subjectId) {
        String userId = requireUserId(auth);
        AttendanceTerm term = attendanceTermService.deleteSubject(userId, termId, subjectId);
        return ResponseEntity.ok(ApiResponse.success("Subject deleted", term));
    }

    private String requireUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new AuthException.UnauthorizedException("User is not authenticated");
        }
        return (String) auth.getPrincipal();
    }
}
