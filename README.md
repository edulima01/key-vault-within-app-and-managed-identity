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

Apesar da técnica ser a mesma, o desenvolvimento da solução número 2 acima é diferente caso esteja utilizando .NET Core com Visual Studio Code, ou se está sendo desenvolvido utilizando Java. Então, no final, são **três** os caminhos a serem percorridos para desenvolver uma aplicação que utiliza a Azure Key Vault como registro das informações sensíveis da aplicação. As especificidades de cada um dos caminhos estão definidos em suas respectivas seções:
- managed-identity-using-dotnet-and-visual-studio (TO-DO: definir url)
- managed-identity-using-dotnet-and-vs-code (TO-DO: definir url)
- service-principal-using-java (TO-DO: definir url)

## Criando um Key Vault e configurando acesso via Managed Identity

## Criando um Service Principal
