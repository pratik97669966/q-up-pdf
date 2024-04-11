# Use the official Node.js 14 image as a base
FROM node:14

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy package.json and package-lock.json to the working directory
COPY package*.json ./

# Install dependencies
RUN npm install
# Download Chromium during build process (assuming PUPPETEER_EXECUTABLE_PATH is set on OnRender)
RUN apt-get update && apt-get install -y chromium

# Set the path to Chromium executable
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium

# Copy the rest of the application code
COPY . .

# Expose port 3000 to the outside world
EXPOSE 3000

# Command to run the application
CMD ["node", "app.js"]