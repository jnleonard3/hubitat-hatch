const { prepareWebSocketUrl } = require('aws-iot-device-sdk/device/index.js')
const http = require("http");

const port = 8236;

const requestListener = function (req, res) {
    let body = '';
    req.on('data', (chunk) => {
        body += chunk;
    });
    req.on('end', () => {
        try {
            const { host, region, awsAccessId, awsSecretKey, awsStsToken } = JSON.parse(body);
            const url = prepareWebSocketUrl({ region, host }, awsAccessId, awsSecretKey, awsStsToken)

            res.write(JSON.stringify({ url })); 
            res.end(); 
        } catch (e) {
            console.error(e);
            res.statusCode = 500;
            res.end();
        }
    });
};

const server = http.createServer(requestListener);
server.listen({ port }, () => {
    console.log(`Server is running on port ${port}`);
});