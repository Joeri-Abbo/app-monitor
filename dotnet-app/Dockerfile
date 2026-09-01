FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build

WORKDIR /build

# Restore first for better layer caching.
COPY DotnetApp.csproj ./
RUN dotnet restore

COPY . .
RUN dotnet publish DotnetApp.csproj -c Release -o /app --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0

# curl backs the container healthcheck in docker-compose.yml.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app ./

ENV ASPNETCORE_HTTP_PORTS=8080
EXPOSE 8080

ENTRYPOINT ["dotnet", "DotnetApp.dll"]
