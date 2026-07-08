FROM php:8.3-apache

# System deps + PHP extensions (apcu backs the Prometheus exporter, opcache for speed).
RUN apt-get update \
    && apt-get install -y --no-install-recommends libzip-dev zip unzip \
    && docker-php-ext-install opcache \
    && pecl install apcu \
    && docker-php-ext-enable apcu \
    && printf "apc.enable_cli=1\n" >> /usr/local/etc/php/conf.d/docker-php-ext-apcu.ini \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Composer binary.
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

# Serve from public/ and enable front-controller rewrites.
ENV APACHE_DOCUMENT_ROOT=/var/www/html/public
RUN sed -ri -e 's!/var/www/html!${APACHE_DOCUMENT_ROOT}!g' /etc/apache2/sites-available/*.conf \
    && sed -ri -e 's!/var/www/!${APACHE_DOCUMENT_ROOT}!g' /etc/apache2/apache2.conf /etc/apache2/conf-available/*.conf \
    && a2enmod rewrite

WORKDIR /var/www/html

# Install dependencies first for better layer caching.
COPY composer.json composer.lock ./
RUN composer install --no-dev --no-interaction --prefer-dist --no-scripts --no-autoloader

COPY . .
RUN composer dump-autoload --optimize --no-dev

EXPOSE 80
