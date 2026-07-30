package com.unisubmit.controller;

import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserApiController {

    private final UserService userService;

    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam(name = "q", required = false) String q) {
        List<Map<String, Object>> result = userService.searchStudents(q).stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getName());
                    StudentProfile sp = u.getStudentProfile();
                    m.put("studentId", sp != null ? sp.getAdmissionNumber() : "");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
