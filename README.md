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

![App Service creation](/resources/images/app-service-creation.png?raw=true)

Após criar, selecione a opção *Identity*:

![App Service identity selection](/resources/images/app-service-select-identity-blade.png?raw=true)

Nessa tela, altere a opção *Status* para *On* na guia *System assigned*, e confirme as alterações:

![App Service turning managed identity on](/resources/images/app-service-create-managed-identity.png?raw=true)

Ao final, o identificador do *Service Principal* criado para o App Service é exibido:

![App Service managed identity created](/resources/images/app-service-managed-identity-created.png?raw=true)

Depois de criar o App Service e sua Managed Identity, vamos para a criação do Key Vault.

Na tela de criação do Key Vault, informe um nome, um grupo de recurso, uma região e o pricing tier, e clique em *Access policies*:

![Key Vault creation](/resources/images/key-vault-creation.png?raw=true)

Na tela de políticas de acesso, você verá que o usuário criando o Key Vault já tem acesso ao mesmo. Clique em *Add New*:

![Key Vault access policies](/resources/images/key-vault-add-access-policy.png?raw=true)

Clique em *Select principal*, pesquise o nome do App Service criado, clique no nome do App Service informado e clique em Select:

![Key Vault new access policy](/resources/images/key-vault-select-managed-identity-principal.png?raw=true)

Informe as políticas de *Get* e de *List* para *Secret permissions* e clique em Ok:

![Key Vault new access policy permissions](/resources/images/key-vault-permissions-selection.png?raw=true)

Aguarde a criação do Key Vault. Quando terminar, o App Service já terá acesso ao Key Vault. Caso a aplicação sendo desenvolvida usa .NET Core com Visual Studio, pule para a seção [Implementar o acesso no código](#implementar-o-acesso-no-código). Caso contrário, siga na próxima seção.

## Criar um Service Principal (caso o desenvolvimento não seja feito com .NET Core, ou seja .NET Core sem Visual Studio)

Se a aplicação sendo desenvolvida não utilizar .NET Core, ou utilizar mas não usar Visual Studio, então deve ser configurado um *Service Principal* para que o desenvolvimento local possa acessar o Key Vault.

Nesse caso, primeiro crie uma *App Registration* (o mesmo que um Service Principal), informando um nome e uma url de redirecionamento (não se preocupe com essa url, pode ser uma url local, como no print abaixo):

![Service Principal creation](/resources/images/service-principal-creation.png?raw=true)

Após a criação, clique em *Settings*, e em *Keys* para a criação do *Client Secret* que deverá ser utilizado no desenvolvimento local:

![Service Principal add key](/resources/images/service-principal-add-secret.png?raw=true)

Informe uma descrição e uma data de expiração para essa chave de acesso e clique em Salvar (lembre-se de salvar essa chave de acesso, pois ela não será exibida novamente):

![Service Principal key created](/resources/images/service-principal-client-secret-creation.png?raw=true)

Por fim, navegue até o Key Vault, selecione *Access policies* e adicione esse *Service Principal* da mesma forma que o *App Service* foi adicionado na seção anterior.

Ao final, o Key Vault será acessível pelo seu criador, pelo *App Service* e pelo *Service Principal*:

![Key Vault completed](/resources/images/key-vault-final-access-policies.png?raw=true)

Toda a configuração necessária está finalizada. É hora de implementar o acesso no código.

## Implementar o acesso no código

Para continuar esse tutorial, siga cada um dos caminhos possíveis:
- [Desenvolvimento .NET Core com Visual Studio](#desenvolvimento-net-core-com-visual-studio)
- [Desenvolvimento .NET Core com Visual Studio Code](#desenvolvimento-net-core-com-visual-studio-code)
- [Desenvolvimento Java](#desenvolvimento-java)

O código para cada um dos caminhos acima estão disponíveis, respectivamente, em [/dotnet-and-visual-studio](/dotnet-and-visual-studio), [/dotnet-and-vs-code](/dotnet-and-vs-code) e [/java-and-vs-code](/java-and-vs-code).

### Desenvolvimento .NET Core com Visual Studio

Este é o caminho que necessita a menor quantidade de configuração. Basta incluir o código que inicia as configurações do Key Vault ao criar o WebHostBuilder e, opcionalmente, registrar uma classe que representa esta configuração.

Primeiro, no seu projeto .NET Core, adicione os pacotes *Microsoft.Azure.KeyVault*, *Microsoft.Azure.Services.AppAuthentication* e *Microsoft.Extensions.Configuration.AzureKeyVault*. Esses pacotes são responsáveis por, respectivamente, ler dados do Key Vault, autenticar o desenvolvedor quando rodando local ou autenticar usando as credenciais da aplicação quando rodando na rede da Azure, e por incluir os dados do Key Vault na configuração da aplicação quando esta é iniciada:

![.NET Core Visual Studio Nuget packages](/resources/images/dotnet-vs-npm-packages.png?raw=true)

Após isso, devemos dizer à aplicação para incluir em suas configurações os dados KeyVault. Inicialmente, o arquivo Program.cs deve estar parecido com este:
```C#
public static IWebHostBuilder CreateWebHostBuilder(string[] args) =>
    WebHost.CreateDefaultBuilder(args)
        .UseStartup<Startup>();
```

Vamos então dizer ao WebHostBuilder como obter configurações do Key Vault. Para isso, vamos adicionar o método *ConfigureAppConfiguration* e obter a url do Key Vault que foi criado nos passos anteriores:

![Key Vault url](/resources/images/key-vault-url.png?raw=true)

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

![.NET Core Visual Studio Azure authentication](/resources/images/dotnet-vs-authenticate-azure.png?raw=true)

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

![Browser user name before key vault](/resources/images/browser-before-key-vault.png?raw=true)

Vamos agora mover essa string de conexão para o Key Vault. Acesse o Key Vault, escolha Secrets e depois Generate/Import:

![Key Vault generate secret](/resources/images/key-vault-generate-secret.png?raw=true)

Preencha o nome, o valor e garanta que ele está habilitado. Há um motivo para criarmos o o nome do segredo usando os dois híphens, que explicaremos mais adiante:

![Key Vault secret creation](/resources/images/key-vault-secret-creation.png?raw=true)

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

![Browser after key vault](/resources/images/browser-after-key-vault.png?raw=true)

E pronto! Nossa string de conexão está segura no Key Vault, e não temos ela armazenada no código.

Podemos agora publicar essa aplicação e obtermos os mesmos resultados:

![Browser deployed on Azure](/resources/images/browser-deployed-on-azure.png?raw=true)

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
                "AzureServicesAuthConnectionString": "RunAs=App;TenantId=c1305c4d-6577-4c06-a7ee-720c5182f2d9;AppId=89afb9e3-c0b3-4936-ae84-7d3069871111;AppKey=NsXvxfx0cytAGL4altG4qUo7Ig3+S19ufZAeg+lb4p8=;"
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

# Referências

- [How to use managed identities for App Service and Azure Functions](https://docs.microsoft.com/en-us/azure/app-service/overview-managed-identity)
- [Azure Key Vault Configuration Provider in ASP.NET Core](https://docs.microsoft.com/en-us/aspnet/core/security/key-vault-configuration?view=aspnetcore-2.2)
- [Service-to-service authentication to Azure Key Vault using .NET](https://docs.microsoft.com/en-us/azure/key-vault/service-to-service-authentication)
- [Get keyvault secrets using Spring api with Managed Service Identities](https://stackoverflow.com/questions/55187035/get-keyvault-secrets-using-spring-api-with-managed-service-identities)
- [Read Azure key vault secret through MSI in Java](https://stackoverflow.com/questions/51750846/read-azure-key-vault-secret-through-msi-in-java)
