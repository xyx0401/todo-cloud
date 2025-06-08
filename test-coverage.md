# 测试覆盖方案

## 概述

本文档描述了Spring Cloud Todo微服务系统的完整测试策略，包括单元测试、集成测试和端到端测试。

## 测试架构

```
测试金字塔
├── E2E测试 (5%) - 端到端测试
├── 集成测试 (25%) - 服务间集成
└── 单元测试 (70%) - 业务逻辑测试
```

## 1. 单元测试

### 1.1 Todo Service 单元测试

#### TodoPageController 测试
```java
// src/test/java/com/example/todoservice/controller/TodoPageControllerTest.java
@WebMvcTest(TodoPageController.class)
@MockBean({JdbcTemplate.class})
class TodoPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodoService todoService;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("获取主页 - 已登录用户")
    void testIndex_LoggedInUser() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        session.setAttribute("username", "admin");
        
        List<TodoItem> todos = Arrays.asList(
            createTodoItem(1L, "Test Todo", "Work", false, 1L)
        );
        
        when(todoService.findByUserId(1L)).thenReturn(todos);

        // When & Then
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("todos", hasSize(1)))
                .andExpect(model().attribute("username", "admin"));
    }

    @Test
    @DisplayName("获取主页 - 未登录用户重定向")
    void testIndex_NotLoggedIn() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("创建新Todo")
    void testCreateTodo() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        
        TodoItem savedTodo = createTodoItem(1L, "New Todo", "Work", false, 1L);
        when(todoService.save(any(TodoItem.class))).thenReturn(savedTodo);

        // When & Then
        mockMvc.perform(post("/")
                .session(session)
                .param("name", "New Todo")
                .param("category", "Work"))
                .andExpect(status().is3xxRedirection())
                .andExpected(redirectedUrl("/"));
        
        verify(todoService).save(argThat(todo -> 
            todo.getName().equals("New Todo") && 
            todo.getCategory().equals("Work") &&
            todo.getUserId().equals(1L)
        ));
    }

    @Test
    @DisplayName("更新Todo")
    void testUpdateTodo() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        
        TodoItem existingTodo = createTodoItem(1L, "Old Todo", "Work", false, 1L);
        when(todoService.findById(1L)).thenReturn(Optional.of(existingTodo));

        // When & Then
        mockMvc.perform(post("/update/1")
                .session(session)
                .param("name", "Updated Todo")
                .param("category", "Personal")
                .param("complete", "on"))
                .andExpect(status().is3xxRedirection())
                .andExpected(redirectedUrl("/"));
        
        verify(todoService).save(argThat(todo -> 
            todo.getName().equals("Updated Todo") && 
            todo.getCategory().equals("Personal") &&
            todo.getComplete()
        ));
    }

    @Test
    @DisplayName("删除Todo")
    void testDeleteTodo() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        
        TodoItem existingTodo = createTodoItem(1L, "Test Todo", "Work", false, 1L);
        when(todoService.findById(1L)).thenReturn(Optional.of(existingTodo));

        // When & Then
        mockMvc.perform(post("/delete/1")
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpected(redirectedUrl("/"));
        
        verify(todoService).deleteById(1L);
    }

    private TodoItem createTodoItem(Long id, String name, String category, Boolean complete, Long userId) {
        TodoItem todo = new TodoItem();
        todo.setId(id);
        todo.setName(name);
        todo.setCategory(category);
        todo.setComplete(complete);
        todo.setUserId(userId);
        return todo;
    }
}
```

#### LoginController 测试
```java
// src/test/java/com/example/todoservice/controller/LoginControllerTest.java
@WebMvcTest(LoginController.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("显示登录页面")
    void testShowLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("登录成功")
    void testLogin_Success() throws Exception {
        // Given
        Map<String, Object> userRow = new HashMap<>();
        userRow.put("id", 1L);
        userRow.put("username", "admin");
        userRow.put("password", "$2a$10$encodedPassword");
        
        when(jdbcTemplate.queryForMap(anyString(), eq("admin")))
                .thenReturn(userRow);
        when(passwordEncoder.matches("password", "$2a$10$encodedPassword"))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/login")
                .param("username", "admin")
                .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(request().sessionAttribute("userId", 1L))
                .andExpect(request().sessionAttribute("username", "admin"));
    }

    @Test
    @DisplayName("登录失败 - 用户不存在")
    void testLogin_UserNotFound() throws Exception {
        // Given
        when(jdbcTemplate.queryForMap(anyString(), eq("nonexistent")))
                .thenThrow(new EmptyResultDataAccessException(1));

        // When & Then
        mockMvc.perform(post("/login")
                .param("username", "nonexistent")
                .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("登录失败 - 密码错误")
    void testLogin_WrongPassword() throws Exception {
        // Given
        Map<String, Object> userRow = new HashMap<>();
        userRow.put("id", 1L);
        userRow.put("username", "admin");
        userRow.put("password", "$2a$10$encodedPassword");
        
        when(jdbcTemplate.queryForMap(anyString(), eq("admin")))
                .thenReturn(userRow);
        when(passwordEncoder.matches("wrongpassword", "$2a$10$encodedPassword"))
                .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/login")
                .param("username", "admin")
                .param("password", "wrongpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}
```

#### TodoService 测试
```java
// src/test/java/com/example/todoservice/service/TodoServiceTest.java
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    @Test
    @DisplayName("根据用户ID查找Todo列表")
    void testFindByUserId() {
        // Given
        Long userId = 1L;
        List<TodoItem> expectedTodos = Arrays.asList(
            createTodoItem(1L, "Todo 1", "Work", false, userId),
            createTodoItem(2L, "Todo 2", "Personal", true, userId)
        );
        
        when(todoRepository.findByUserIdOrderByIdDesc(userId))
                .thenReturn(expectedTodos);

        // When
        List<TodoItem> actualTodos = todoService.findByUserId(userId);

        // Then
        assertThat(actualTodos).hasSize(2);
        assertThat(actualTodos.get(0).getName()).isEqualTo("Todo 1");
        assertThat(actualTodos.get(1).getName()).isEqualTo("Todo 2");
        verify(todoRepository).findByUserIdOrderByIdDesc(userId);
    }

    @Test
    @DisplayName("根据ID查找Todo")
    void testFindById() {
        // Given
        Long todoId = 1L;
        TodoItem expectedTodo = createTodoItem(todoId, "Test Todo", "Work", false, 1L);
        
        when(todoRepository.findById(todoId))
                .thenReturn(Optional.of(expectedTodo));

        // When
        Optional<TodoItem> actualTodo = todoService.findById(todoId);

        // Then
        assertThat(actualTodo).isPresent();
        assertThat(actualTodo.get().getName()).isEqualTo("Test Todo");
        verify(todoRepository).findById(todoId);
    }

    @Test
    @DisplayName("保存Todo")
    void testSave() {
        // Given
        TodoItem todoToSave = createTodoItem(null, "New Todo", "Work", false, 1L);
        TodoItem savedTodo = createTodoItem(1L, "New Todo", "Work", false, 1L);
        
        when(todoRepository.save(todoToSave)).thenReturn(savedTodo);

        // When
        TodoItem result = todoService.save(todoToSave);

        // Then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("New Todo");
        verify(todoRepository).save(todoToSave);
    }

    @Test
    @DisplayName("删除Todo")
    void testDeleteById() {
        // Given
        Long todoId = 1L;

        // When
        todoService.deleteById(todoId);

        // Then
        verify(todoRepository).deleteById(todoId);
    }

    private TodoItem createTodoItem(Long id, String name, String category, Boolean complete, Long userId) {
        TodoItem todo = new TodoItem();
        todo.setId(id);
        todo.setName(name);
        todo.setCategory(category);
        todo.setComplete(complete);
        todo.setUserId(userId);
        return todo;
    }
}
```

### 1.2 User Service 单元测试

#### UserController 测试
```java
// user-service/src/test/java/com/example/userservice/controller/UserControllerTest.java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("获取所有用户")
    void testGetAllUsers() throws Exception {
        // Given
        List<UserDTO> users = Arrays.asList(
            createUserDTO(1L, "admin", "admin@test.com", "13800138000"),
            createUserDTO(2L, "user1", "user1@test.com", "13900139000")
        );
        
        when(userService.findAll()).thenReturn(users);

        // When & Then
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username", is("admin")))
                .andExpect(jsonPath("$[1].username", is("user1")));
    }

    @Test
    @DisplayName("根据ID获取用户")
    void testGetUserById() throws Exception {
        // Given
        UserDTO user = createUserDTO(1L, "admin", "admin@test.com", "13800138000");
        when(userService.findById(1L)).thenReturn(user);

        // When & Then
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.email", is("admin@test.com")));
    }

    @Test
    @DisplayName("用户不存在时返回404")
    void testGetUserById_NotFound() throws Exception {
        // Given
        when(userService.findById(999L)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("创建新用户")
    void testCreateUser() throws Exception {
        // Given
        UserDTO newUser = createUserDTO(null, "testuser", "test@test.com", "13700137000");
        UserDTO savedUser = createUserDTO(3L, "testuser", "test@test.com", "13700137000");
        
        when(userService.save(any(UserDTO.class))).thenReturn(savedUser);

        // When & Then
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(3)))
                .andExpect(jsonPath("$.username", is("testuser")));
    }

    @Test
    @DisplayName("创建用户 - 参数验证失败")
    void testCreateUser_ValidationFailed() throws Exception {
        // Given
        UserDTO invalidUser = createUserDTO(null, "", "", ""); // 空用户名

        // When & Then
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest());
    }

    private UserDTO createUserDTO(Long id, String username, String email, String phone) {
        UserDTO user = new UserDTO();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(1);
        return user;
    }
}
```

### 1.3 Auth Service 单元测试

#### AuthController 测试
```java
// auth-service/src/test/java/com/example/authservice/controller/AuthControllerTest.java
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("用户认证成功")
    void testAuthenticate_Success() throws Exception {
        // Given
        AuthRequest request = new AuthRequest("admin", "password");
        AuthResponse response = new AuthResponse("jwt-token", 3600, "Bearer");
        
        when(authService.authenticate("admin", "password")).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/auth/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token", is("jwt-token")))
                .andExpect(jsonPath("$.expiresIn", is(3600)))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    @Test
    @DisplayName("用户认证失败")
    void testAuthenticate_Failed() throws Exception {
        // Given
        AuthRequest request = new AuthRequest("admin", "wrongpassword");
        when(authService.authenticate("admin", "wrongpassword"))
                .thenThrow(new AuthenticationException("Invalid credentials"));

        // When & Then
        mockMvc.perform(post("/api/auth/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("验证JWT令牌")
    void testValidateToken() throws Exception {
        // Given
        TokenRequest request = new TokenRequest("valid-jwt-token");
        TokenValidation validation = new TokenValidation(true, "admin", LocalDateTime.now().plusHours(1));
        
        when(authService.validateToken("valid-jwt-token")).thenReturn(validation);

        // When & Then
        mockMvc.perform(post("/api/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.username", is("admin")));
    }
}
```

## 2. 集成测试

### 2.1 数据库集成测试

#### TodoService 数据库集成测试
```java
// todo-service/src/test/java/com/example/todoservice/integration/TodoServiceIntegrationTest.java
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TodoServiceIntegrationTest {

    @Container
    static MySQL8Container mysql = new MySQL8Container("mysql:8.0")
            .withDatabaseName("tododb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TodoService todoService;

    @Autowired
    private TodoRepository todoRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
    }

    @Test
    @DisplayName("保存和查询Todo项目")
    void testSaveAndFindTodo() {
        // Given
        TodoItem todo = new TodoItem();
        todo.setName("Integration Test Todo");
        todo.setCategory("Testing");
        todo.setComplete(false);
        todo.setUserId(1L);

        // When
        TodoItem savedTodo = todoService.save(todo);
        List<TodoItem> userTodos = todoService.findByUserId(1L);

        // Then
        assertThat(savedTodo.getId()).isNotNull();
        assertThat(savedTodo.getName()).isEqualTo("Integration Test Todo");
        assertThat(userTodos).hasSize(1);
        assertThat(userTodos.get(0).getName()).isEqualTo("Integration Test Todo");
    }

    @Test
    @DisplayName("更新Todo项目")
    void testUpdateTodo() {
        // Given
        TodoItem todo = new TodoItem();
        todo.setName("Original Todo");
        todo.setCategory("Work");
        todo.setComplete(false);
        todo.setUserId(1L);
        TodoItem savedTodo = todoService.save(todo);

        // When
        savedTodo.setName("Updated Todo");
        savedTodo.setComplete(true);
        TodoItem updatedTodo = todoService.save(savedTodo);

        // Then
        assertThat(updatedTodo.getName()).isEqualTo("Updated Todo");
        assertThat(updatedTodo.getComplete()).isTrue();
    }

    @Test
    @DisplayName("删除Todo项目")
    void testDeleteTodo() {
        // Given
        TodoItem todo = new TodoItem();
        todo.setName("Todo to Delete");
        todo.setCategory("Test");
        todo.setComplete(false);
        todo.setUserId(1L);
        TodoItem savedTodo = todoService.save(todo);

        // When
        todoService.deleteById(savedTodo.getId());
        Optional<TodoItem> deletedTodo = todoService.findById(savedTodo.getId());

        // Then
        assertThat(deletedTodo).isEmpty();
    }
}
```

### 2.2 微服务间集成测试

#### Gateway路由集成测试
```java
// gateway-service/src/test/java/com/example/gatewayservice/integration/GatewayRoutingTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.gateway.routes[0].id=todo-service",
    "spring.cloud.gateway.routes[0].uri=http://localhost:8081",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/**"
})
class GatewayRoutingTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @MockBean
    private DiscoveryClient discoveryClient;

    @Test
    @DisplayName("Gateway路由到Todo Service")
    void testRouteToTodoService() {
        // Given
        String gatewayUrl = "http://localhost:" + port + "/";
        
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(gatewayUrl, String.class);

        // Then
        // 应该返回重定向到登录页面或者登录页面内容
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.FOUND);
    }
}
```

### 2.3 完整服务集成测试

```java
// integration-tests/src/test/java/com/example/integration/FullStackIntegrationTest.java
@SpringBootTest(classes = {TodoServiceApplication.class})
@TestPropertySource(locations = "classpath:application-test.properties")
@Testcontainers
class FullStackIntegrationTest {

    @Container
    static MySQL8Container mysql = new MySQL8Container("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Test
    @DisplayName("完整用户流程测试")
    void testCompleteUserFlow() {
        String baseUrl = "http://localhost:" + port;
        
        // 1. 访问主页应该重定向到登录页
        ResponseEntity<String> homeResponse = restTemplate.getForEntity(baseUrl + "/", String.class);
        assertThat(homeResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(homeResponse.getHeaders().getLocation().toString()).contains("/login");

        // 2. 获取登录页面
        ResponseEntity<String> loginPageResponse = restTemplate.getForEntity(baseUrl + "/login", String.class);
        assertThat(loginPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. 登录（需要先设置测试用户数据）
        // ...实际测试中需要设置数据库测试数据
    }
}
```

## 3. 端到端测试

### 3.1 Selenium Web测试

```java
// e2e-tests/src/test/java/com/example/e2e/TodoE2ETest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
class TodoE2ETest {

    @Container
    static BrowserWebDriverContainer chrome = new BrowserWebDriverContainer()
            .withCapabilities(new ChromeOptions());

    private WebDriver driver;

    @BeforeEach
    void setUp() {
        driver = chrome.getWebDriver();
    }

    @Test
    @DisplayName("用户登录和创建Todo的完整流程")
    void testUserLoginAndCreateTodo() {
        // 1. 访问应用
        driver.get("http://host.testcontainers.internal:8080");

        // 2. 应该重定向到登录页面
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.titleContains("登录"));

        // 3. 输入登录凭据
        WebElement usernameField = driver.findElement(By.name("username"));
        WebElement passwordField = driver.findElement(By.name("password"));
        WebElement loginButton = driver.findElement(By.type("submit"));

        usernameField.sendKeys("admin");
        passwordField.sendKeys("password");
        loginButton.click();

        // 4. 验证登录成功，进入主页
        wait.until(ExpectedConditions.titleContains("Todo List"));
        assertThat(driver.getTitle()).contains("Todo List");

        // 5. 创建新的Todo
        WebElement addButton = driver.findElement(By.linkText("Add New Todo"));
        addButton.click();

        wait.until(ExpectedConditions.titleContains("Add Todo"));
        
        WebElement nameField = driver.findElement(By.name("name"));
        WebElement categoryField = driver.findElement(By.name("category"));
        WebElement saveButton = driver.findElement(By.type("submit"));

        nameField.sendKeys("E2E Test Todo");
        categoryField.sendKeys("Testing");
        saveButton.click();

        // 6. 验证Todo创建成功
        wait.until(ExpectedConditions.titleContains("Todo List"));
        List<WebElement> todoItems = driver.findElements(By.className("todo-item"));
        assertThat(todoItems).hasSizeGreaterThan(0);
        
        boolean foundTestTodo = todoItems.stream()
            .anyMatch(item -> item.getText().contains("E2E Test Todo"));
        assertThat(foundTestTodo).isTrue();
    }

    @Test
    @DisplayName("未授权用户访问受保护页面")
    void testUnauthorizedAccess() {
        driver.get("http://host.testcontainers.internal:8080/new");
        
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.urlContains("/login"));
        
        assertThat(driver.getCurrentUrl()).contains("/login");
    }
}
```

## 4. 性能测试

### 4.1 JMeter测试计划

```xml
<!-- todo-performance-test.jmx -->
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan testname="Todo Service Performance Test">
      <elementProp name="TestPlan.arguments" elementType="Arguments"/>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <!-- 登录测试 -->
      <ThreadGroup testname="Login Load Test">
        <intProp name="ThreadGroup.num_threads">50</intProp>
        <intProp name="ThreadGroup.ramp_time">30</intProp>
        <longProp name="ThreadGroup.duration">300</longProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy testname="Login Request">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <intProp name="HTTPSampler.port">8080</intProp>
          <stringProp name="HTTPSampler.path">/login</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="username" elementType="HTTPArgument">
                <stringProp name="Argument.name">username</stringProp>
                <stringProp name="Argument.value">admin</stringProp>
              </elementProp>
              <elementProp name="password" elementType="HTTPArgument">
                <stringProp name="Argument.name">password</stringProp>
                <stringProp name="Argument.value">password</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        
        <!-- 响应断言 -->
        <ResponseAssertion testname="Response Assertion">
          <collectionProp name="Asserion.test_strings">
            <stringProp>302</stringProp>
          </collectionProp>
          <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
        </ResponseAssertion>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### 4.2 性能测试脚本

```java
// performance-tests/src/test/java/com/example/performance/TodoPerformanceTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class TodoPerformanceTest {

    private static final int CONCURRENT_USERS = 100;
    private static final int TEST_DURATION_SECONDS = 60;

    @Test
    @DisplayName("Todo创建性能测试")
    void testTodoCreationPerformance() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    // 模拟用户创建Todo的操作
                    createTodoForUser(userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(TEST_DURATION_SECONDS, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Performance Test Results:");
        System.out.println("Duration: " + duration + "ms");
        System.out.println("Successful requests: " + successCount.get());
        System.out.println("Failed requests: " + errorCount.get());
        System.out.println("Requests per second: " + (successCount.get() * 1000.0 / duration));

        // 断言性能要求
        assertThat(successCount.get()).isGreaterThan(CONCURRENT_USERS * 0.95); // 95%成功率
        assertThat(duration).isLessThan(TEST_DURATION_SECONDS * 1000 * 1.2); // 不超过预期时间的120%
    }

    private void createTodoForUser(int userId) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("name", "Performance Test Todo " + userId);
        body.add("category", "Performance");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        if (!response.getStatusCode().is3xxRedirection()) {
            throw new RuntimeException("Request failed with status: " + response.getStatusCode());
        }
    }
}
```

## 5. 测试配置

### 5.1 测试依赖配置

```xml
<!-- pom.xml 测试依赖 -->
<dependencies>
    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Testcontainers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mysql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>selenium</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- WireMock for API mocking -->
    <dependency>
        <groupId>com.github.tomakehurst</groupId>
        <artifactId>wiremock-jre8</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- AssertJ for fluent assertions -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 5.2 测试配置文件

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    
  cloud:
    nacos:
      discovery:
        enabled: false
        
logging:
  level:
    com.example: DEBUG
    org.springframework.web: DEBUG
```

## 6. 持续集成测试

### 6.1 GitHub Actions 工作流

```yaml
# .github/workflows/test.yml
name: Test Suite

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: password
          MYSQL_DATABASE: testdb
        ports:
          - 3306:3306
        options: --health-cmd="mysqladmin ping" --health-interval=10s --health-timeout=5s --health-retries=3

    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 8
      uses: actions/setup-java@v3
      with:
        java-version: '8'
        distribution: 'temurin'
        
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        
    - name: Run unit tests
      run: mvn clean test
      
    - name: Generate test report
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Unit Test Results
        path: '**/target/surefire-reports/*.xml'
        reporter: java-junit
        
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        file: ./target/site/jacoco/jacoco.xml

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 8
      uses: actions/setup-java@v3
      with:
        java-version: '8'
        distribution: 'temurin'
        
    - name: Run integration tests
      run: mvn clean verify -Pintegration-tests
      
  e2e-tests:
    runs-on: ubuntu-latest
    needs: integration-tests
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 8
      uses: actions/setup-java@v3
      with:
        java-version: '8'
        distribution: 'temurin'
        
    - name: Start services
      run: |
        docker-compose -f docker-compose.test.yml up -d
        sleep 30
        
    - name: Run E2E tests
      run: mvn clean test -Pe2e-tests
      
    - name: Stop services
      run: docker-compose -f docker-compose.test.yml down
```

## 7. 测试报告

### 7.1 覆盖率配置

```xml
<!-- pom.xml JaCoCo插件配置 -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.7</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## 8. 测试执行计划

### 8.1 测试执行顺序

1. **单元测试** (每次代码提交)
   - 控制器层测试
   - 服务层测试
   - 数据访问层测试

2. **集成测试** (每次Pull Request)
   - 数据库集成测试
   - 服务间集成测试
   - API集成测试

3. **端到端测试** (每日自动化)
   - 关键用户流程测试
   - 跨浏览器兼容性测试

4. **性能测试** (每周/每次发布前)
   - 负载测试
   - 压力测试
   - 容量测试

### 8.2 测试目标

- **代码覆盖率**: 最低80%
- **单元测试执行时间**: 少于2分钟
- **集成测试执行时间**: 少于10分钟
- **E2E测试执行时间**: 少于30分钟
- **性能测试响应时间**: 平均响应时间少于200ms
- **性能测试吞吐量**: 支持每秒1000个并发请求 