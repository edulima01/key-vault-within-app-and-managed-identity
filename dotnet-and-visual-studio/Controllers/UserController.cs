using System;
using System.Collections.Generic;
using System.Data.SqlClient;
using System.Linq;
using System.Threading.Tasks;
using dotnet_and_visual_studio.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Configuration;

namespace dotnet_and_visual_studio.Controllers
{
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
}
