package com.aitranslator.admin;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class AdminPageController {
 @GetMapping("/admin") public String index(){return "redirect:/admin/";}
 @GetMapping("/admin/") public String app(){return "forward:/admin/index.html";}
}
