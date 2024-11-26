const express = require('express');
const os = require('os');
const { execSync } = require('child_process');

const app = express();
const PORT = 3001;


app.listen(PORT, (error) =>{
    // To run this:
    // node server.js
    if(!error)
        console.log("Server is successfully running, and App is listening on port "+ PORT)
    else 
        console.log("Error occurred, server can't start", error);
    }
);

app.get('/sysInfo', (req, res) => {
  const systemInfo = getService2SystemInfo();
  res.json(systemInfo);
});

function getService2SystemInfo(){
    const ip_address = Object.values(os.networkInterfaces())
      .flat()
      .find((iface) => iface.family === 'IPv4' && !iface.internal).address;

    
    const processes = runCLICommand("top -b -n 1 | sed '1,3d'")

    const diskSpace = runCLICommand("df -h / | tail -1 | awk '{print $4}'");

    const uptime = `up ${Math.floor(os.uptime() / 3600)} hour, ${Math.floor((os.uptime() % 3600) / 60)} minutes`;

    return {
      ip: ip_address,
      topProcesses: processes,
      diskSpace: diskSpace,
      uptime: uptime,
    };
}

function runCLICommand(command){
    try{
        return execSync(command).toString().trim();
    }
    catch(error){
        console.error("Error occurred while running command: ", command);
        return "Error running command: ", error;
    }
}