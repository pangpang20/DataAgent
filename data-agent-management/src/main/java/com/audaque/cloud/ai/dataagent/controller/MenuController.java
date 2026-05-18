/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.audaque.cloud.ai.dataagent.controller;

import com.audaque.cloud.ai.dataagent.dto.auth.MenuTreeDTO;
import com.audaque.cloud.ai.dataagent.dto.menu.MenuCreateRequest;
import com.audaque.cloud.ai.dataagent.dto.menu.MenuUpdateRequest;
import com.audaque.cloud.ai.dataagent.entity.SysMenu;
import com.audaque.cloud.ai.dataagent.service.menu.MenuService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class MenuController {

	private final MenuService menuService;

	@PostMapping
	@PreAuthorize("hasAuthority('system:menu:*')")
	public ApiResponse<SysMenu> createMenu(@Valid @RequestBody MenuCreateRequest request) {
		SysMenu menu = menuService.createMenu(request);
		return ApiResponse.success("创建菜单成功", menu);
	}

	@PutMapping
	@PreAuthorize("hasAuthority('system:menu:*')")
	public ApiResponse<SysMenu> updateMenu(@Valid @RequestBody MenuUpdateRequest request) {
		SysMenu menu = menuService.updateMenu(request);
		return ApiResponse.success("更新菜单成功", menu);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('system:menu:*')")
	public ApiResponse<Void> deleteMenu(@PathVariable Long id) {
		menuService.deleteMenu(id);
		return ApiResponse.success("删除菜单成功");
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('system:menu:*')")
	public ApiResponse<SysMenu> getMenuById(@PathVariable Long id) {
		SysMenu menu = menuService.getMenuById(id);
		return ApiResponse.success("获取菜单成功", menu);
	}

	@GetMapping("/tree")
	public ApiResponse<List<MenuTreeDTO>> getMenuTree() {
		List<MenuTreeDTO> tree = menuService.listMenuTree();
		return ApiResponse.success("获取菜单树成功", tree);
	}

	@GetMapping("/list")
	@PreAuthorize("hasAuthority('system:menu:*')")
	public ApiResponse<List<SysMenu>> listMenus() {
		List<SysMenu> menus = menuService.listAllMenus();
		return ApiResponse.success("获取菜单列表成功", menus);
	}

}
