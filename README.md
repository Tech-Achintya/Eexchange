# Eexchange Marketplace

Eexchange is a comprehensive online marketplace application that allows users to buy, sell, and exchange items. It is composed of a robust backend built with Java and Spring Boot, and a modern, responsive frontend developed with React and Vite.

## Table of Contents
- [Features](#features)
- [Technology Stack](#technology-stack)
- [API Endpoints](#api-endpoints)
- [Getting Started](#getting-started)
- [Folder Structure](#folder-structure)

## Features
- **User Authentication**: Secure signup and login with OTP verification and JWT-based authentication.
- **Item Management**: Users can create, view, update, and delete item listings.
- **User Profiles**: Manage user accounts and view listed items.
- **Admin Dashboard**: Specialized administrative controls to oversee the marketplace operations.
- **Categories**: Browse items by organized categories.
- **Email Notifications**: Integrated email service for activities like OTP distribution.

## Technology Stack

### Backend
- **Java 21**: Core programming language.
- **Spring Boot 3.x**: Framework for rapid application development.
- **Spring Security**: For handling authentication and authorization.
- **MongoDB**: NoSQL database for flexible data storage.
- **JWT (JSON Web Tokens)**: For secure API communications.
- **Maven**: Build and dependency management.
- **Lombok**: To reduce boilerplate code.
- **JavaMailSender**: For sending emails (e.g., OTPs).

### Frontend
- **React 19**: UI library for building the single-page application.
- **Vite**: Next-generation frontend tooling for faster builds and hot module replacement.
- **React Router DOM**: For client-side routing.
- **Vanilla CSS**: For styling the components.

## API Endpoints (Overview)

### Authentication (`/api/auth` or similar)
- `POST /signup` - Register a new user.
- `POST /login` - Authenticate user and return a JWT.
- `POST /verify-otp` - Verify the OTP sent to the user's email.

### Items (`/api/items` or similar)
- `GET /` - Retrieve all available items.
- `POST /` - Create a new item listing.
- `PUT /{id}` - Update an existing item.
- `DELETE /{id}` - Remove an item listing.

### User & Admin
- `GET /api/user/profile` - Retrieve user profile details.
- `GET /api/admin/dashboard` - Access admin-specific data and metrics.

## Getting Started

Follow these steps to clone and run the project locally.

### Prerequisites
- [Java 21+](https://www.oracle.com/java/technologies/downloads/)
- [Maven](https://maven.apache.org/install.html)
- [Node.js 18+](https://nodejs.org/)
- [MongoDB](https://www.mongodb.com/try/download/community) (Local or Atlas URL)

### 1. Clone the Repository
```bash
git clone https://github.com/Tech-Achintya/Eexchange.git
cd Eexchange
```

### 2. Backend Setup
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Configure your `application.properties` or `application.yml` in `src/main/resources` with your MongoDB URI and Mail server credentials.
3. Build and run the backend server:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
   The backend should now be running on `http://localhost:8080`.

### 3. Frontend Setup
1. Open a new terminal and navigate to the frontend directory:
   ```bash
   cd Frontend/Eexchange
   ```
2. Install the required dependencies:
   ```bash
   npm install
   ```
3. Set up the `.env` file with the backend API URL if necessary (e.g., `VITE_API_BASE_URL=http://localhost:8080`).
4. Start the development server:
   ```bash
   npm run dev
   ```
   The frontend should now be running on `http://localhost:5173` (or the port specified by Vite).

## Deployment Note
The backend and frontend can be deployed individually (e.g., Backend on Render/Heroku and Frontend on Vercel/Netlify). Make sure to properly configure CORS on the backend to accept requests from your deployed frontend domain.
