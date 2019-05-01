# Usando Key Vault para armazenar informações de forma segura na Azure usando .NET Core ou Java

Normalmente, uma aplicação (que pode ser um App Service, uma Azure Function, um Azure Batch, ou outras) geralmente precisa de acessar outros recursos dentro da rede da Azure, como por exemplo um banco de dados Azure SQL DB com as informações da aplicação.

Mas como provemos acesso de forma segura a esse recurso dentro de nossa aplicação?

### Uma solução inicial

Em aplicações onde há interação com um usuário (um App Service, por exemplo), uma solução seria utilizar a identidade do usuário logado na aplicação para acesso ao Azure SQL DB, dando acesso ao Azure SQL DB a **todos** os usuários cadastrados no AD que pudessem utilizar a aplicação. Obviamente, o gerenciamento dessa solução é bastante complicado, e bem possível de gerar brechas de segurança, já que os usuários poderiam acessar o Azure SQL DB de qualquer ferramenta que permita acesso ao recurso, e não apenas via aplicação.

### Minimizando o acesso ao recurso compartilhado

Mas e para aplicações onde não há interação com um usuário, como na execução de uma Azure Function?

Uma solução seria dar acesso via AD ao Azure SQL DB a somente um usuário do AD, e utilizar o login/senha deste usuário de forma fixa dentro da aplicação. Esta solução apenas minimiza o problema do acesso ao recurso, já que este usuário ainda poderia acessar o Azure SQL DB de qualquer ferramenta que acesse o recurso, e é uma grande brecha de segurança (mesmo que para ambientes de desenvolvimento), pois utiliza uma senha diretamente. O que acontece, por exemplo, se a organização possui uma política de troca de senhas periodicamente?

### Service Principal

Para não ter que fazer uma aplicação usar um usuário físico, a Azure permite a criação de um usuário especificamente para uma aplicação. Este usuário representa não uma pessoa do mundo real, mas um serviço. Este tipo de usuário e chamado de __*Service Principal*__. Neste caso, o Service Principal é identificado por um Id (chamado de App Id ou Client Id), e pode ser autenticado utilizando um Client Secret (uma chave segura gerada automaticamente pela Azure). Este usuário pode ser utilizado em uma aplicação para acessar um recurso. Nesse caso, o acesso ao recurso seria limitado somente à aplicação que utilizasse esse Service Principal. Mas continuaríamos utilizando uma senha (o Client Secret) diretamente no código.

### Managed Identity

Para resolver este último problema, a Azure criou um tipo especial de Service Principal: o __*Managed Identity*__. Neste caso, ao habilitar um Managed Identity para um recurso (um App Service, por exemplo), a Azure garante que esta aplicação possa ser autenticada dentro da rede da Azure de forma segura sem a necessidade de compartilhar um Client Secret na aplicação. É a própria Azure quem garante que a aplicação rodando dentro da sua rede terá acesso ao Client Secret automaticamente.

> Tecnicamente falando, a aplicação, ao usar os recursos de autenticação da Azure, irá automaticamente acessar um endpoint de autenticação que somente é acessível de dentro da rede da Azure, e com o client secret sendo injetado pela própria Azure no ambiente em que a aplicação está executando.

Dessa forma, usando um Managed Identity, qualquer aplicação que estiver rodando dentro da rede da Azure poderá usufruir dessa funcionalidade para não precisar trafegar Client Secrets dentro do código da aplicação. Esta técnica tem a abreviação MSI dentro da Azure (*Managed Secret Identity*).

Isso permite um código da seguinte forma:

```C#
using (KeyVaultClient client = new KeyVaultClient(new KeyVaultClient.AuthenticationCallback(new AzureServiceTokenProvider().KeyVaultTokenCallback)))
{
    SecretBundle secret = await client.GetSecretAsync("secret url");
    string _connectionString = secret.Value;
}
```

```java
AppServiceMSICredentials credentials = new AppServiceMSICredentials(AzureEnvironment.AZURE);
KeyVaultClient keyVaultClient = new KeyVaultClient(credentials);
keyVaultClient.getSecret("vault url","secret name");
```

Observe que não há nenhuma necessidade de informar um id ou senha para acesso ao Key Vault! Para um código em produção, esta configuração é suficiente.

Mas e para desenvolvermos e testarmos estas soluções localmente? Uma forma seria a criação de uma máquina virtual dentro da Azure para o desenvolvedor, criar um Managed Identity para esta máquina virtual e dar acesso a ela ao Key Vault.

Mas nem sempre essa solução é possível, e às vezes precisamos desenvolver soluções fora do ambiente da Azure. O que fazer então?

### Os diferentes caminhos de desenvolvimento

A solução para o ambiente de desenvolvimento vai depender das tecnologias e ambientes de desenvolvimento (IDE's) utilizadas na aplicação:
1. Utilizando .NET Core com Visual Studio, a SDK da Azure consegue autenticar utilizando o usuário logado no Visual Studio e, dessa forma, basta dar acesso ao desenvolvedor no Key Vault diretamente, que já seria suficiente. Como provavelmente teríamos um Key Vault para o ambiente de desenvolvimento, e um Key Vault para o ambiente de produção, essa solução seria a mais interessante do ponto de vista de segurança.
2. Agora, caso o desenvolvimento seja feito com .NET Core mas sem o Visual Studio, ou então o desenvolvimento seja feito em Java, então a solução é diferente: é necessário criar um Service Principal com acesso ao ambiente de desenvolvimento, e, quando em ambiente local, autenticar utilizando este Service Principal, ao invés da autenticação utilizando Managed Identity.
   - Apesar da técnica ser a mesma, o desenvolvimento da solução número 2 acima é diferente caso esteja utilizando .NET Core com Visual Studio Code, ou se está sendo desenvolvido utilizando Java. Então, no final, são **três** os caminhos a serem percorridos para desenvolver uma aplicação que utiliza a Azure Key Vault como registro das informações sensíveis da aplicação.

# Tutorial para implementação

Com todos os conceitos necessários sendo explicados acima, vamos exemplificar os passos que foram definidos:
1. [Criar um Key Vault e um App Service, e dar acesso ao Key Vault para o App Service](#criar-um-key-vault-e-um-app-service-e-dar-acesso-ao-key-vault-para-o-app-service)
2. [Criar um Service Principal (caso o desenvolvimento não seja feito com .NET Core, ou seja .NET Core sem Visual Studio)](#criar-um-service-principal-caso-o-desenvolvimento-não-seja-feito-com-net-core-ou-seja-net-core-sem-visual-studio)
3. [Implementar o acesso no código](#implementar-o-acesso-no-código)

## Criar um Key Vault e um App Service, e dar acesso ao Key Vault para o App Service

Em primeiro lugar, vamos criar um App Service. A criação é padrão, preenchendo o nome, grupo de recurso, região, e escolhendo o plano de serviço:

![App Service creation](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/app-service-creation.png?raw=true)

Após criar, selecione a opção *Identity*:

![App Service identity selection](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/app-service-select-identity-blade.png?raw=true)

Nessa tela, altere a opção *Status* para *On* na guia *System assigned*, e confirme as alterações:

![App Service turning managed identity on](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/app-service-create-managed-identity.png?raw=true)

Ao final, o identificador do *Service Principal* criado para o App Service é exibido:

![App Service managed identity created](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/app-service-managed-identity-created.png?raw=true)

Depois de criar o App Service e sua Managed Identity, vamos para a criação do Key Vault.

Na tela de criação do Key Vault, informe um nome, um grupo de recurso, uma região e o pricing tier, e clique em *Access policies*:

![Key Vault creation](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-creation.png?raw=true)

Na tela de políticas de acesso, você verá que o usuário criando o Key Vault já tem acesso ao mesmo. Clique em *Add New*:

![Key Vault access policies](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-add-access-policy.png?raw=true)

Clique em *Select principal*, pesquise o nome do App Service criado, clique no nome do App Service informado e clique em Select:

![Key Vault new access policy](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-select-managed-identity-principal.png?raw=true)

Informe as políticas de *Get* e de *List* para *Secret permissions* e clique em Ok:

![Key Vault new access policy permissions](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-permissions-selection.png?raw=true)

Aguarde a criação do Key Vault. Quando terminar, o App Service já terá acesso ao Key Vault. Caso a aplicação sendo desenvolvida usa .NET Core com Visual Studio, pule para a seção [Implementar o acesso no código](#implementar-o-acesso-no-código). Caso contrário, siga na próxima seção.

## Criar um Service Principal (caso o desenvolvimento não seja feito com .NET Core, ou seja .NET Core sem Visual Studio)

Se a aplicação sendo desenvolvida não utilizar .NET Core, ou utilizar mas não usar Visual Studio, então deve ser configurado um *Service Principal* para que o desenvolvimento local possa acessar o Key Vault.

Nesse caso, primeiro crie uma *App Registration* (o mesmo que um Service Principal), informando um nome e uma url de redirecionamento (não se preocupe com essa url, pode ser uma url local, como no print abaixo):

![Service Principal creation](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/service-principal-creation.png?raw=true)

Após a criação, clique em *Settings*, e em *Keys* para a criação do *Client Secret* que deverá ser utilizado no desenvolvimento local:

![Service Principal add key](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/service-principal-add-secret.png?raw=true)

Informe uma descrição e uma data de expiração para essa chave de acesso e clique em Salvar (lembre-se de salvar essa chave de acesso, pois ela não será exibida novamente):

![Service Principal key created](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/service-principal-client-secret-creation.png?raw=true)

Por fim, navegue até o Key Vault, selecione *Access policies* e adicione esse *Service Principal* da mesma forma que o *App Service* foi adicionado na seção anterior.

Ao final, o Key Vault será acessível pelo seu criador, pelo *App Service* e pelo *Service Principal*:

![Key Vault completed](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-final-access-policies.png?raw=true)

Toda a configuração necessária está finalizada. É hora de implementar o acesso no código.

## Implementar o acesso no código

Para continuar esse tutorial, siga cada um dos caminhos possíveis:
- [Desenvolvimento .NET Core com Visual Studio](#desenvolvimento-net-core-com-visual-studio)
- [Desenvolvimento .NET Core com Visual Studio Code](#desenvolvimento-net-core-com-visual-studio-code)
- [Desenvolvimento Java](#desenvolvimento-java)

O código para cada um dos caminhos acima estão disponíveis, respectivamente, em [/dotnet-and-visual-studio](https://github.com/edulima01/keyvault-managed-identity/blob/master/dotnet-and-visual-studio), [/dotnet-and-vs-code](https://github.com/edulima01/keyvault-managed-identity/blob/master/dotnet-and-vs-code) e [/java-and-vs-code](https://github.com/edulima01/keyvault-managed-identity/blob/master/java-and-vs-code).

### Desenvolvimento .NET Core com Visual Studio

Este é o caminho que necessita a menor quantidade de configuração. Basta incluir o código que inicia as configurações do Key Vault ao criar o WebHostBuilder e, opcionalmente, registrar uma classe que representa esta configuração.

Primeiro, no seu projeto .NET Core, adicione os pacotes *Microsoft.Azure.KeyVault*, *Microsoft.Azure.Services.AppAuthentication* e *Microsoft.Extensions.Configuration.AzureKeyVault*. Esses pacotes são responsáveis por, respectivamente, ler dados do Key Vault, autenticar o desenvolvedor quando rodando local ou autenticar usando as credenciais da aplicação quando rodando na rede da Azure, e por incluir os dados do Key Vault na configuração da aplicação quando esta é iniciada:

![.NET Core Visual Studio Nuget packages](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/dotnet-vs-npm-packages.png?raw=true)

Após isso, devemos dizer à aplicação para incluir em suas configurações os dados KeyVault. Inicialmente, o arquivo Program.cs deve estar parecido com este:
```C#
public static IWebHostBuilder CreateWebHostBuilder(string[] args) =>
    WebHost.CreateDefaultBuilder(args)
        .UseStartup<Startup>();
```

Vamos então dizer ao WebHostBuilder como obter configurações do Key Vault. Para isso, vamos adicionar o método *ConfigureAppConfiguration* e obter a url do Key Vault que foi criado nos passos anteriores:

![Key Vault url](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-url.png?raw=true)

```C#
public static IWebHostBuilder CreateWebHostBuilder(string[] args) =>
    WebHost.CreateDefaultBuilder(args)
        .ConfigureAppConfiguration((context, configuration) =>
        {
            string azureVaultUrl = configuration.Build()["KeyVault:Url"];
        })
        .UseStartup<Startup>();
```

Observe que também devemos atualizar o arquivo *appSettings.json* com a chave "KeyVault:Url":
```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Warning"
    }
  },
  "KeyVault": {
    "Url": "https://<key-vault-name>.vault.azure.net"
  },
  "AllowedHosts": "*"
}
```

Agora, incluímos a classe que será responsável por autenticar o usuário correto na Azure e obter o token de acesso para o Key Vault. Essa classe é a *AzureServiceTokenProvider*. Em um ambiente local, ela irá autenticar na Azure com o usuário autenticado no Visual Studio. Quando o código for executado na rede da Azure, ela irá utilizar o endpoint do MSI para autenticar a própria *Managed Identity* da aplicação.

Para garantir que o desenvolvedor está logado no Visual Studio, acesse __Tools > Options > Azure Service Authentication > Account Selection__ e selecione a conta que você deseja usar (ou adicione uma nova):

![.NET Core Visual Studio Azure authentication](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/dotnet-vs-authenticate-azure.png?raw=true)

Inclua o token provider no código:
```C#
public static IWebHostBuilder CreateWebHostBuilder(string[] args) =>
    WebHost.CreateDefaultBuilder(args)
        .ConfigureAppConfiguration((context, configuration) =>
        {
            string azureVaultUrl = configuration.Build()["KeyVault:Url"];
            AzureServiceTokenProvider azureServiceTokenProvider = new AzureServiceTokenProvider();
        })
        .UseStartup<Startup>();
```

Vamos incluir agora o cliente que irá acessar o Key Vault. Esse cliente espera, em seu construtor, um callback que é responsável por efetivamente autenticar o usuário na Azure e obter o token de acesso para o Key Vault usar. Nós iremos passar o callback existente no *AzureServiceTokenProvider* que fará toda a autenticação pra gente:
```C#
public static IWebHostBuilder CreateWebHostBuilder(string[] args) =>
    WebHost.CreateDefaultBuilder(args)
        .ConfigureAppConfiguration((context, configuration) =>
        {
            string azureVaultUrl = configuration.Build()["KeyVault:Url"];
            AzureServiceTokenProvider azureServiceTokenProvider = new AzureServiceTokenProvider();

            KeyVaultClient keyVaultClient = new KeyVaultClient(new KeyVaultClient.AuthenticationCallback(azureServiceTokenProvider.KeyVaultTokenCallback));
        })
        .UseStartup<Startup>();
```

Por fim, basta adicionar o *KeyVaultClient* como uma fonte de configurações para a aplicação:
```C#
public static IWebHostBuilder CreateWebHostBuilder(string[] args) =>
    WebHost.CreateDefaultBuilder(args)
        .ConfigureAppConfiguration((context, configuration) =>
        {
            string azureVaultUrl = configuration.Build()["KeyVault:Url"];
            AzureServiceTokenProvider azureServiceTokenProvider = new AzureServiceTokenProvider();

            KeyVaultClient keyVaultClient = new KeyVaultClient(new KeyVaultClient.AuthenticationCallback(azureServiceTokenProvider.KeyVaultTokenCallback));
            configuration.AddAzureKeyVault(azureVaultUrl, keyVaultClient, new DefaultKeyVaultSecretManager());
        })
        .UseStartup<Startup>();
```

Para utilizar as chaves do Key Vault, vamos alterar um dos *Controllers* da API para obter a string de conexão do KeyVault, acessar o banco de dados, e retornar o nome do usuário utilizado na conexão. O código original é esse:
```C#
[Route("api/[controller]")]
[ApiController]
public class UserController : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<string>> Get()
    {
        string connectionString = "Server=tcp:<your-database-server>.database.windows.net,1433;Initial Catalog=<your-database-name>;User ID=<user-name>;Password=<password>;";
        using (SqlConnection connection = new SqlConnection(connectionString))
        {
            connection.Open();
            using (SqlCommand command = connection.CreateCommand())
            {
                command.CommandText = "SELECT SYSTEM_USER";
                object result = command.ExecuteScalar();
                return Ok(result);
            }
        }
    }
}

```

Este código produz o resultado:

![Browser user name before key vault](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/browser-before-key-vault.png?raw=true)

Vamos agora mover essa string de conexão para o Key Vault. Acesse o Key Vault, escolha Secrets e depois Generate/Import:

![Key Vault generate secret](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-generate-secret.png?raw=true)

Preencha o nome, o valor e garanta que ele está habilitado. Há um motivo para criarmos o o nome do segredo usando os dois híphens, que explicaremos mais adiante:

![Key Vault secret creation](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/key-vault-secret-creation.png?raw=true)

Agora, iremos alterar o Controller para receber uma instância de *IConfiguration* no construtor via DI. A partir dele, podemos ler os segredos do Key Vault, trocando os dois híphens pelo símbolo de dois pontos (":"). Vamos ler a string de conexão dessa forma e salvá-la em uma propriedade, utilizá-la para acessar o banco, e retorná-la para a tela junto com o resultado de nossa consulta:
```C#
[Route("api/[controller]")]
[ApiController]
public class UserController : ControllerBase
{
    public string ConnectionString { get; private set; }

    public UserController(IConfiguration configuration)
    {
        // The secret name in the key vault is "Secrets--ConnectionString", but two dashes are changed into colons
        this.ConnectionString = configuration["Secrets:ConnectionString"];
    }

    [HttpGet]
    public async Task<ActionResult<string>> Get()
    {
        using (SqlConnection connection = new SqlConnection(this.ConnectionString))
        {
            connection.Open();
            using (SqlCommand command = connection.CreateCommand())
            {
                command.CommandText = "SELECT SYSTEM_USER";
                object result = command.ExecuteScalar();
                return Ok(new { ConnectionString, result });
            }
        }
    }
}
```

Essa alteração produz o resultado:

![Browser after key vault](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/browser-after-key-vault.png?raw=true)

E pronto! Nossa string de conexão está segura no Key Vault, e não temos ela armazenada no código.

Podemos agora publicar essa aplicação e obtermos os mesmos resultados:

![Browser deployed on Azure](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/browser-deployed-on-azure.png?raw=true)

__*Bônus:* Utilizando classe fortemente tipada para organizar os segredos__

Utilizamos o nome do secret no Key Vault com os dois híphens porque o Key Vault infere que os dois hífens indicam uma estrutura hierárquica. Quando essa estrutura é levada para o config, o padrão do *IConfiguration* para hierarquia é de dois pontos (":"). Por isso que no código de obter o valor do Secret, utilizamos a string "Secrets:ConnectionString".

E a vantagem de se utilizar essas estruturas hierárquicas, é que podemos utilizar o padrão *IOptions* do .NET Core para já embutir esses valores de forma tipada. No nosso caso, criaremos a classe *Secrets*:
```C#
public class Secrets
{
    public string ConnectionString { get; set; }
}
```

Incluímos no arquivo *Startup* o código para carregar os valores do *IConfiguration* na classe *Secrets* e provemos essa classe como um serviço:
```C#
public void ConfigureServices(IServiceCollection services)
{
    // Register the IOptions object
    services.Configure<Secrets>(Configuration.GetSection("Secrets"));
    // Explicitly register the Secrets object by delegating to the IOptions object
    services.AddSingleton(resolver => resolver.GetRequiredService<IOptions<Secrets>>().Value);
}
```

E por último alteramos o Controller para receber uma instância da classe *Secrets* ao invés de *IConfiguration*:
```C#
[Route("api/[controller]")]
[ApiController]
public class UserController : ControllerBase
{
    public string ConnectionString { get; private set; }

    public UserController(Secrets secrets)
    {
        this.ConnectionString = secrets.ConnectionString;
    }

    [HttpGet]
    public async Task<ActionResult<string>> Get()
    {
        using (SqlConnection connection = new SqlConnection(this.ConnectionString))
        {
            connection.Open();
            using (SqlCommand command = connection.CreateCommand())
            {
                command.CommandText = "SELECT SYSTEM_USER";
                object result = command.ExecuteScalar();
                return Ok(new { ConnectionString, result });
            }
        }
    }
}
```

### Desenvolvimento .NET Core com Visual Studio Code

Para utilizar o Visual Studio Code, é simples. Todas as alterações de código que foram feitas na seção anterior (.NET Core com Visual Studio) devem ser feitas aqui também. E a única configuração é no arquivo *launch.json*, onde é feita a inclusão da variável de ambiente *AzureServicesAuthConnectionString*. O pacote *Microsoft.Azure.Services.AppAuthentication* já está programado para, rodando em ambiente local, caso não encontre um usuário do Visual Studio, ele utilize as credenciais desta variável de ambiente:

```json
{
   "version": "0.2.0",
   "configurations": [
        {
            "name": ".NET Core Launch (web)",
            "type": "coreclr",
            "request": "launch",
            "preLaunchTask": "build",
            "program": "${workspaceFolder}/bin/Debug/netcoreapp2.1/AppUsingKeyVault.dll",
            "args": [],
            "cwd": "${workspaceFolder}",
            "stopAtEntry": false,
            "launchBrowser": {
                "enabled": true
            },
            "env": {
                "ASPNETCORE_ENVIRONMENT": "Development",
                "AzureServicesAuthConnectionString": "RunAs=App;TenantId=<YOUR TENANT ID>;AppId=<CLIENT ID OF THE SERVICE PRINCIPAL>;AppKey=<CLIENT SECRET OF THE SERVICE PRINCIPAL>;"
            },
            "sourceFileMap": {
                "/Views": "${workspaceFolder}/Views"
            }
        },
        {
            "name": ".NET Core Attach",
            "type": "coreclr",
            "request": "attach",
            "processId": "${command:pickProcess}"
        }
    ]
}
```

E pronto, tudo já está configurado para execução local do projeto utilizando .NET Core com Visual Studio Code.

### Desenvolvimento Java

Para o desenvolvimento java, iremos utilizar o *spring boot* para acelerar o desenvolvimento da aplicação.

Para isso, primeiro acesse *http://start.spring.io* e selecione as opções padrão para o Spring clicando em *More options*: utilizando *maven*, Java, versão 2.1.4 do Spring Boot, e informe o grupo, artefato, nome, descrição do projeto, o tipo de empacotamento como sendo *jar*, e a versão do Java 8.

![Spring boot initial configuration](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/spring-boot-initial-config.png?raw=true)

Na caixa de *Search dependencies to add*, digite e depois selecione o pacote referente a: *web*, *jdbc*, *sql server* e *Azure Key Vault*.

![Spring boot dependencies](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/spring-boot-dependencies.png?raw=true)

Clique em Generate Project, baixe o arquivo zip gerado e extraia ele para uma pasta, que será a raiz do projeto.

Iremos acertar o arquivo *pom.xml*. Para isso, primeiro altere a tag *properties* para atualizar a versão dos pacotes da Azure para 2.1.4, e inclua as propriedades que serão utilizadas para o deploy da aplicação na azure. Preencha essas propriedades com o nome do grupo de recursos, o nome do App Service (que será um servidor Linux), o nome da região e o tamanho da máquina associada ao Service Plan.

__Obs.:__ não é possível ter um App Service rodando Windows e outro rodando Linux dentro do mesmo grupo de recursos.

```xml
<properties>
    <java.version>1.8</java.version>
    <azure.version>2.1.4</azure.version>
    <deployment.resource-group>app-using-key-vault-java</deployment.resource-group>
    <deployment.app-name>app-using-key-vault-java</deployment.app-name>
    <deployment.region>eastus</deployment.region>
    <deployment.pricing-tier>B1</deployment.pricing-tier>
</properties>
```

Na seção *dependency management*, devemos fixar a versão da biblioteca do Key Vault como sendo 1.2.0, para corrigir um erro no Azure Spring Boot, que causa um conflito de referências (ver [Fail to get Key Vault access through MSI](https://github.com/Microsoft/azure-spring-boot/issues/621)).

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.microsoft.azure</groupId>
            <artifactId>azure-spring-boot-bom</artifactId>
            <version>${azure.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.microsoft.azure</groupId>
            <artifactId>azure-keyvault</artifactId>
            <version>1.2.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Por fim, adicionar o plugin Azure Webapp Maven no ciclo de build, para que ele faça o deploy da aplicação na Azure.

```xml
<plugin>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>azure-webapp-maven-plugin</artifactId>
    <version>1.4.0</version>
    <configuration>
        <deploymentType>jar</deploymentType>

        <!-- configure app to run on port 80, required by App Service -->
        <appSettings>
            <property> 
                <name>JAVA_OPTS</name> 
                <value>-Dserver.port=80</value> 
            </property> 
        </appSettings>

        <!-- Web App information -->
        <resourceGroup>${deployment.resource-group}</resourceGroup>
        <appName>${deployment.app-name}</appName>
        <region>${deployment.region}</region>
        <pricingTier>${deployment.pricing-tier}</pricingTier>

        <!-- Java Runtime Stack for Web App on Linux-->
        <linuxRuntime>jre8</linuxRuntime>
    </configuration>
</plugin>
```

Para que o deploy aconteça, é necessário instalar o Azure CLI e fazer o login:

```shell
az login
az account set --subscription <subscription id>
```

Com as configurações feitas, precisamos fazer as alterações no código para utilizar o Key Vault como fonte de configuração para o projeto. Para isso, na pasta *src/main/resources*, crie os arquivos *application.properties* e *application-local.properties*.

O arquivo *application.properties* terá as configurações gerais do projeto, e as informações básicas do Key Vault.

```INI
azure.keyvault.enabled=true
azure.keyvault.uri=https://<key vault name>.vault.azure.net
```

O arquivo *application-local.properties* será usado quando a aplicação for executada localmente, e aí, será incluído o Client Id e o Client Secret do Service Principal criado para essa situação, nos passos anteriores.

```INI
azure.keyvault.client-id=<client id>
azure.keyvault.client-key=<client secret>
```

Agora, vamos criar um arquivo que será o Controller REST pra receber as chamadas. Crie um arquivo *UserController.java* com o seguinte código:

```java
@RestController
public class UserController {
    
    // The secret name in the key vault is "Secrets-ConnectionString", but one dash is changed to a dot
    @Value("${secrets.connectionstring}")
    private String connectionString;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @RequestMapping("/api/User")
    public Result index() {
        String userName = (String)jdbcTemplate.queryForObject("SELECT SYSTEM_USER", String.class);
        Result result = new Result();
        result.setConnectionString(connectionString);
        result.setResult(userName);
        return result;
    }
}
```

Onde *Result* é uma classe POJO com as propriedades *result* e *connectionString*. Observe que nessa classe estamos usando um *JdbcTemplate* para acessar o banco, que já foi configurado com a string de conexão do key vault. A configuração dele está na classe *Application*:

```java
@Value("${secrets.connectionstring}")
private String connectionString;

@Bean
public DataSource dataSource()
{
    return DataSourceBuilder.create().url(connectionString).build();
}

@Bean
public JdbcTemplate jdbcTemplate(DataSource dataSource)
{
    return new JdbcTemplate(dataSource);
}
```

Nessa classe eu injeto o valor do *Secret* na propriedade *connectionString*, utilizo essa propriedade pra criar um *DataSource*, e uso este para criar o *JdbcTemplate*.

Pronto. Podemos compilar o código e rodar localmente com o comando:

```shell
mvn clean package spring-boot:run -Dspring-boot.run.profiles=local
```

Aí, ele irá executar utilizando o arquivo *properties* local, que contém o Client ID e o Client Secret, junto do arquivo *properties* padrão. Basta essas propriedades estarem presente em tempo de execução para o Azure Key Vault entender que ele deve se autenticar usando um *Service Principal*.

Para realizar o deploy do projeto, executar o comando:

```shell
mvn azure-webapp:deploy
```

Ele irá utilizar a conta que está logada usando o Azure CLI nos passos anteriores para realizar o deploy da aplicação no grupo de recursos e App Service indicados no arquivo *pom*.

Como a versão que está rodando na Azure __não__ especifica um profile para o Spring Boot, então somente o arquivo *properties* padrão é carregado e, por não ter sido especificado um Client ID e Client Secret, o Azure Key Vault irá tentar se autenticar utilizando o *Managed Identity*.

![Browser java deployed on Azure](https://github.com/edulima01/keyvault-managed-identity/blob/master/resources/images/browser-java-deployed-on-azure.png?raw=true)

__*Obs.:*__ nos exemplos acima, utilizamos o acesso à chave usando a notação padrão do Java com Spring, que é colocar um ponto para gerar hierarquias. Em Java, a biblioteca da Azure Key Vault, diferentemente da biblioteca em .NET, troca um hífen por um ponto nos nomes das chaves para gerar a hierarquia. Então, nos nossos exemplos, deveria ter uma chave chamada *Secrets-ConnectionString* no Key Vault para que ela fosse encontrada. Mas, as duas versões são disponibilizadas no ambiente Java, tanto a versão com o hífen, quanto a versão com o ponto. Essa diferença de padronização de nomenclatura deve ser levada em conta ao criar um Key Vault compartilhado entre dois projetos.

# Referências
- [Azure Key Vault Configuration Provider in ASP.NET Core](https://docs.microsoft.com/en-us/aspnet/core/security/key-vault-configuration?view=aspnetcore-2.2)
- [Get keyvault secrets using Spring api with Managed Service Identities](https://stackoverflow.com/questions/55187035/get-keyvault-secrets-using-spring-api-with-managed-service-identities)
- [Read Azure key vault secret through MSI in Java](https://stackoverflow.com/questions/51750846/read-azure-key-vault-secret-through-msi-in-java)
- [Service-to-service authentication to Azure Key Vault using .NET](https://docs.microsoft.com/en-us/azure/key-vault/service-to-service-authentication)
- [How to use managed identities for App Service and Azure Functions](https://docs.microsoft.com/en-us/azure/app-service/overview-managed-identity)
- [Authentication samples for Azure Key Vault using the Azure Java SDK](https://github.com/Azure-Samples/key-vault-java-authentication)
- [Azure SDKs for Java](https://github.com/Azure/azure-sdk-for-java)
- [Spring on Azure](https://docs.microsoft.com/en-us/java/azure/spring-framework/?view=azure-java-stable)
- [Azure Key Vault Secrets Spring boot starter](https://github.com/Microsoft/azure-spring-boot/tree/master/azure-spring-boot-starters/azure-keyvault-secrets-spring-boot-starter)
- [What is managed identities for Azure resources?](https://docs.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/overview)
- [Fail to get Key Vault access through MSI](https://github.com/Microsoft/azure-spring-boot/issues/621)
- [Maven Plugin for Azure App Service](https://github.com/Microsoft/azure-maven-plugins/tree/develop/azure-webapp-maven-plugin)
